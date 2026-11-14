// Android EGL/libmpv Surface renderer. Deliberately uses runtime symbol resolution:
// the app builds with no MPV binary, but actual MPV is only offered when
// ABI-matched libmpv.so and its transitive NDK libraries are packaged.
// ABI definitions correspond to mpv's public client.h, render.h, render_gl.h.
#include <jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <dlfcn.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <algorithm>
#include <cstdio>
#include <cctype>
#include <cstdlib>
#include <sstream>
#include <vector>
#include <memory>
#include <mutex>
#include <string>
#include <thread>

namespace {
std::mutex mpvErrorMutex;
std::string mpvError = "MPV runtime has not been started";
void setMpvError(const std::string& value) {
    std::lock_guard<std::mutex> guard(mpvErrorMutex);
    mpvError = value;
}
std::string getMpvError() {
    std::lock_guard<std::mutex> guard(mpvErrorMutex);
    return mpvError;
}
struct mpv_handle;
struct mpv_render_context;
struct RenderParam { int type; void *data; };
struct OpenGlInit { void *(*get_proc_address)(void*, const char*); void* ctx; };
struct OpenGlFbo { int fbo; int w; int h; int internal_format; };
using Create = mpv_handle*(*)();
using Option = int(*)(mpv_handle*,const char*,const char*);
using OptionNode = int(*)(mpv_handle*,const char*,int,void*);
using Initialize = int(*)(mpv_handle*);
using Command = int(*)(mpv_handle*,const char*const*);
using Destroy = void(*)(mpv_handle*);
using GetProperty = int(*)(mpv_handle*, const char*, int, void*);
// Public libmpv client.h event ABI. Event data is consumed immediately;
// never persist or display source URLs, provider headers, or log messages.
struct MpvEvent { int event_id; int error; uint64_t reply_userdata; void* data; };
struct MpvEndFile { int reason; int error; };
using WaitEvent = const MpvEvent*(*)(mpv_handle*, double);
using MpvFree = void(*)(void*);
using RenderCreate = int(*)(mpv_render_context**,mpv_handle*,RenderParam*);
using Render = int(*)(mpv_render_context*,RenderParam*);
using RenderUpdate = uint64_t(*)(mpv_render_context*);
using RenderFree = void(*)(mpv_render_context*);
using RenderCallback = void(*)(mpv_render_context*, void(*)(void*),void*);

struct Api {
    void* library = nullptr;
    Create create = nullptr;
    Option option = nullptr;
    OptionNode optionNode = nullptr;
    Initialize initialize = nullptr;
    Command command = nullptr;
    Destroy destroy = nullptr;
    GetProperty getProperty = nullptr;
    WaitEvent waitEvent = nullptr;
    MpvFree freeProperty = nullptr;
    RenderCreate renderCreate = nullptr;
    Render render = nullptr;
    RenderUpdate renderUpdate = nullptr;
    RenderFree renderFree = nullptr;
    RenderCallback renderCallback = nullptr;
    ~Api() { if(library) dlclose(library); }
    bool load() {
        library=dlopen("libmpv.so",RTLD_NOW|RTLD_LOCAL);
        if(!library) {
            const char* detail = dlerror();
            setMpvError(std::string("dlopen(libmpv.so): ") + (detail ? detail : "library not found"));
            return false;
        }
#define RESOLVE(field, symbol) field=reinterpret_cast<decltype(field)>(dlsym(library,symbol)); if(!field) {setMpvError("libmpv missing exported symbol: " symbol);return false;}
        RESOLVE(create,"mpv_create"); RESOLVE(option,"mpv_set_option_string");
        RESOLVE(initialize,"mpv_initialize"); RESOLVE(command,"mpv_command");
        RESOLVE(destroy,"mpv_terminate_destroy");
        RESOLVE(renderCreate,"mpv_render_context_create"); RESOLVE(render,"mpv_render_context_render");
        RESOLVE(renderUpdate,"mpv_render_context_update");
        RESOLVE(renderFree,"mpv_render_context_free");
        RESOLVE(renderCallback,"mpv_render_context_set_update_callback");
#undef RESOLVE
        optionNode = reinterpret_cast<OptionNode>(dlsym(library,"mpv_set_option"));
        getProperty = reinterpret_cast<GetProperty>(dlsym(library,"mpv_get_property"));
        waitEvent = reinterpret_cast<WaitEvent>(dlsym(library,"mpv_wait_event"));
        freeProperty = reinterpret_cast<MpvFree>(dlsym(library,"mpv_free"));
        setMpvError("");
        return true;
    }
};
struct MpvNodeList;
struct MpvNode {
    union { char* string; int flag; int64_t int64; double dbl; MpvNodeList* list; void* ptr; } u;
    int format;
};
struct MpvNodeList { int num; MpvNode* values; char** keys; };

// HTTP headers are validated before JNI and again here. Never log their values.
bool validHeaderName(const std::string& name) {
    if(name.empty() || name.size()>64 || !std::isalpha(static_cast<unsigned char>(name[0])))return false;
    for(unsigned char c : name) if(!std::isalnum(c) && c!='-')return false;
    std::string lower=name;
    std::transform(lower.begin(),lower.end(),lower.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});
    return lower!="host" && lower!="connection" && lower!="content-length" &&
        lower!="transfer-encoding" && lower!="proxy-authorization" &&
        lower!="proxy-connection" && lower!="upgrade" && lower!="te" && lower!="trailer";
}
bool parseHeaderLines(const std::string& raw,std::vector<std::string>& headers,std::string& userAgent) {
    if(raw.size()>8192)return false;
    std::istringstream input(raw);
    std::string line;
    while(std::getline(input,line)) {
        if(line.empty())continue;
        if(headers.size()>15)return false;
        const auto separator=line.find(": ");
        if(separator==std::string::npos)return false;
        const auto name=line.substr(0,separator), value=line.substr(separator+2);
        if(!validHeaderName(name) || value.empty() || value.size()>2048)return false;
        for(unsigned char c : value)if(c<32 || c==127)return false;
        std::string lower=name;
        std::transform(lower.begin(),lower.end(),lower.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});
        if(lower=="user-agent") userAgent=value;
        else headers.push_back(line);
    }
    return true;
}
void* glproc(void*,const char* name) {
    auto address=reinterpret_cast<void*>(eglGetProcAddress(name));
    if (!address) address=dlsym(RTLD_DEFAULT,name);
    return address;
}
struct Worker {
    std::unique_ptr<Api> api;
    ANativeWindow* window = nullptr;
    mpv_handle* player = nullptr;
    std::thread thread;
    std::atomic<bool> stop{false};
    std::atomic<bool> ready{false};
    std::atomic<bool> renderDirty{true};
    std::atomic<bool> forceRedraw{false};
    // Updated only by the JNI status poll while holding player lock.
    // "ready" means MPV parsed the media, not necessarily a displayed frame.
    std::string mediaPhase = "loading";
    int mediaFailure = 0;
    std::mutex lock;
    std::condition_variable cv;
    explicit Worker(ANativeWindow* surface,std::unique_ptr<Api> lib):api(std::move(lib)),window(surface) {}
    ~Worker() {
        stop.store(true);
        cv.notify_all();
        if(thread.joinable())thread.join();
        if(window)ANativeWindow_release(window);
    }
    static void wake(void* p) {
        // The callback only schedules a render; MPV API calls stay on the GL thread.
        auto* w=static_cast<Worker*>(p);
        w->renderDirty.store(true, std::memory_order_release);
        w->cv.notify_all();
    }
    void run(std::string address,bool liveTv,std::vector<std::string> httpHeaders,
             std::string userAgent,int64_t initialMs,bool softwareDecoder) {
        EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        EGLContext context=EGL_NO_CONTEXT;
        EGLSurface surface=EGL_NO_SURFACE;
        mpv_render_context* renderer=nullptr;
        if(display==EGL_NO_DISPLAY||!eglInitialize(display,nullptr,nullptr)){
            setMpvError("Could not initialize Android EGL display");return;
        }
        EGLint attrs[]={EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_SURFACE_TYPE,EGL_WINDOW_BIT,
            EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};
        EGLConfig config=nullptr;EGLint count=0;
        if(!eglChooseConfig(display,attrs,&config,1,&count)||!count){
            setMpvError("EGL ES2 surface configuration unavailable");goto cleanup;
        }
        {
            EGLint ctxAttrs[]={EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE};
            context=eglCreateContext(display,config,EGL_NO_CONTEXT,ctxAttrs);
        }
        if(context==EGL_NO_CONTEXT){setMpvError("EGL ES2 context creation failed");goto cleanup;}
        surface=eglCreateWindowSurface(display,config,window,nullptr);
        if(surface==EGL_NO_SURFACE||!eglMakeCurrent(display,surface,surface,context)){
            setMpvError("Could not attach EGL surface to Android SurfaceView");goto cleanup;
        }
        {
            std::lock_guard<std::mutex> guard(lock);
            player=api->create();
        }
        if(!player){setMpvError("mpv_create returned no player handle");goto cleanup;}
        api->option(player,"vo","libmpv");
        api->option(player,"cache","yes");
        if(liveTv) {
            // Preserve the proven Freeview MPEG-TS/MP2 path exactly.
            api->option(player,"hwdec","no");
            api->option(player,"demuxer-lavf-format","mpegts");
            api->option(player,"demuxer-readahead-secs","1");
            api->option(player,"keep-open","yes");
        } else {
            // Stremio: allow mpv to auto-detect MP4/MKV/HLS/other supported formats.
            // Some Android GLES/libmpv builds cannot import hardware decoder
            // surfaces. Keep auto-safe normally, offer explicit software retry.
            api->option(player,"hwdec",softwareDecoder ? "no" : "auto-safe");
            api->option(player,"demuxer-readahead-secs","6");
            api->option(player,"keep-open","no");
            if(initialMs>0) {
                const std::string seconds=std::to_string(initialMs/1000.0);
                if(api->option(player,"start",seconds.c_str())<0) {
                    setMpvError("MPV resume option unavailable");goto cleanup;
                }
            }
            if(!userAgent.empty() && api->option(player,"user-agent",userAgent.c_str())<0) {
                setMpvError("MPV User-Agent option unavailable");goto cleanup;
            }
            if(!httpHeaders.empty()) {
                if(!api->optionNode){setMpvError("MPV header-array API unavailable");goto cleanup;}
                std::vector<MpvNode> values(httpHeaders.size());
                for(size_t i=0;i<httpHeaders.size();++i) {
                    values[i].u.string=const_cast<char*>(httpHeaders[i].c_str());
                    values[i].format=1; // MPV_FORMAT_STRING
                }
                MpvNodeList list{static_cast<int>(values.size()),values.data(),nullptr};
                MpvNode node{}; node.u.list=&list; node.format=7; // MPV_FORMAT_NODE_ARRAY
                if(api->optionNode(player,"http-header-fields",6,&node)<0){ // MPV_FORMAT_NODE=6
                    setMpvError("MPV could not apply source request headers");goto cleanup;
                }
            }
        }
        if(api->initialize(player)<0){setMpvError("mpv_initialize failed");goto cleanup;}
        {
            char glapi[]="opengl";
            OpenGlInit gl{glproc,nullptr};
            RenderParam params[]={{1,glapi},{2,&gl},{0,nullptr}};
            if(api->renderCreate(&renderer,player,params)<0){
                setMpvError("mpv_render_context_create failed (EGL backend)");goto cleanup;
            }
        }
        api->renderCallback(renderer,wake,this);
        {
            const char* command[]={"loadfile",address.c_str(),"replace",nullptr};
            if(api->command(player,command)<0){
                setMpvError("mpv loadfile command failed");goto cleanup;
            }
        }
        ready=true;
        cv.notify_all();
        while(!stop.load()) {
            // Do not redraw/swap the same frame at 60Hz when DVB playback is
            // paused or the broadcaster emits 25fps video. Repeated EGL swaps
            // can starve Compose scrolling and system gesture animations.
            {
                std::unique_lock<std::mutex> guard(lock);
                cv.wait(guard,[&]{return stop.load() || renderDirty.load(std::memory_order_acquire);});
                if(stop.load()) break;
                renderDirty.store(false, std::memory_order_release);
            }
            // MPV's official callback + update contract tells us if a real
            // frame/redraw is ready. MPV functions are only called on the EGL thread.
            const bool resized = forceRedraw.exchange(false, std::memory_order_acq_rel);
            if((api->renderUpdate(renderer) & 1u) == 0 && !resized) continue;
            EGLint width=0,height=0;
            if(!eglQuerySurface(display,surface,EGL_WIDTH,&width)||
                !eglQuerySurface(display,surface,EGL_HEIGHT,&height)) {
                setMpvError("Android EGL video surface was lost");
                break;
            }
            if(width>0&&height>0) {
                OpenGlFbo fbo{0,width,height,0}; int flip=1;
                RenderParam params[]={{3,&fbo},{4,&flip},{0,nullptr}};
                if(api->render(renderer,params)<0 || !eglSwapBuffers(display,surface)) {
                    setMpvError("Native MPV video-frame rendering failed");
                    break;
                }
            }
        }
    cleanup:
        ready=false;
        if(renderer)api->renderFree(renderer);
        {
            std::lock_guard<std::mutex> guard(lock);
            if(player){api->destroy(player);player=nullptr;}
        }
        eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
        if(surface!=EGL_NO_SURFACE)eglDestroySurface(display,surface);
        if(context!=EGL_NO_CONTEXT)eglDestroyContext(display,context);
        eglTerminate(display);
    }
};
std::mutex global_lock;
std::unique_ptr<Worker> active;
std::string activeOwner;
std::string asString(JNIEnv* env,jstring value) {
    if(!value)return {};
    const char* bytes=env->GetStringUTFChars(value,nullptr);
    if(!bytes)return {};
    std::string out(bytes);env->ReleaseStringUTFChars(value,bytes);return out;
}
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvAvailable(JNIEnv*,jclass) {
    Api api;return api.load();
}
static jboolean startMpvOwned(JNIEnv* env,jobject surface,jstring uri,const char* owner,
                               std::string headerText={},int64_t resumeMs=0,bool softwareDecoder=false) {
    std::string path=asString(env,uri);
    if(!surface || path.size()>8192 || !(path.rfind("http://",0)==0||path.rfind("https://",0)==0)){
        setMpvError("MPV requires a valid Android Surface and HTTP(S) URL");return false;
    }
    std::vector<std::string> headers;
    std::string userAgent;
    if(!parseHeaderLines(headerText,headers,userAgent)){
        setMpvError("Invalid stream request headers");return false;
    }
    if(!headerText.empty() && path.rfind("https://",0)!=0) {
        setMpvError("Header-bearing streams require HTTPS");return false;
    }
    std::unique_ptr<Api> api(new Api());
    if(!api->load())return false;
    ANativeWindow* window=ANativeWindow_fromSurface(env,surface);
    if(!window){setMpvError("ANativeWindow_fromSurface failed");return false;}
    std::lock_guard<std::mutex> guard(global_lock);
    if(active && activeOwner=="livetv" && std::string(owner)=="stremio") {
        ANativeWindow_release(window);
        setMpvError("Close Live TV before starting a Stremio stream");
        return false;
    }
    active.reset();
    activeOwner=owner;
    active.reset(new Worker(window,std::move(api)));
    Worker* worker=active.get();
    const bool liveTv=std::string(owner)=="livetv";
    const int64_t resume=std::clamp<int64_t>(resumeMs,0,24LL*60*60*1000);
    worker->thread=std::thread([worker,path,liveTv,headers=std::move(headers),
                               userAgent=std::move(userAgent),resume,softwareDecoder]() mutable {
        worker->run(path,liveTv,std::move(headers),std::move(userAgent),resume,softwareDecoder);
    });
    std::unique_lock<std::mutex> lock(worker->lock);
    worker->cv.wait_for(lock,std::chrono::seconds(3),[&]{return worker->ready.load()||worker->stop.load();});
    if(!worker->ready.load() && getMpvError().empty()) setMpvError("MPV initialization timed out before EGL/render setup");
    return worker->ready.load();
}
static void stopMpvOwned(const char* owner) {
    std::lock_guard<std::mutex> guard(global_lock);
    if (activeOwner == owner) { active.reset(); activeOwner.clear(); }
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvStart(JNIEnv* env,jclass,jobject surface,jstring uri) {
    return startMpvOwned(env,surface,uri,"fullscreen");
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvStartForT405(JNIEnv* env,jclass,jobject surface,jstring uri) {
    return startMpvOwned(env,surface,uri,"livetv");
}
extern "C" JNIEXPORT void JNICALL Java_com_personalmedia_android_NativeBridge_mpvStop(JNIEnv*,jclass) {
    stopMpvOwned("fullscreen");
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvStartStremio(
    JNIEnv* env,jclass,jobject surface,jstring uri,jstring headerLines,jlong resumeMs,jboolean softwareDecoder) {
    return startMpvOwned(env,surface,uri,"stremio",asString(env,headerLines),static_cast<int64_t>(resumeMs),softwareDecoder==JNI_TRUE);
}
extern "C" JNIEXPORT void JNICALL Java_com_personalmedia_android_NativeBridge_mpvStopStremio(JNIEnv*,jclass) {
    stopMpvOwned("stremio");
}

extern "C" JNIEXPORT void JNICALL Java_com_personalmedia_android_NativeBridge_mpvStopForT405(JNIEnv*,jclass) {
    stopMpvOwned("livetv");
}
extern "C" JNIEXPORT void JNICALL Java_com_personalmedia_android_NativeBridge_mpvPause(JNIEnv*,jclass,jboolean paused) {
    std::lock_guard<std::mutex> guard(global_lock);
    if(active&&active->ready.load()) {
        std::lock_guard<std::mutex> playerGuard(active->lock);
        if(active->player && active->ready.load()) {
            const char* cmd[]={"set","pause",paused?"yes":"no",nullptr};
            active->api->command(active->player,cmd);
        }
    }
}

// STR-4 controls are strictly limited to the Stremio playback owner.
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvPauseStremio(
    JNIEnv*,jclass,jboolean paused) {
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="stremio" || !active || !active->ready.load())return false;
    std::lock_guard<std::mutex> pg(active->lock);
    if(!active->player)return false;
    const char* cmd[]={"set","pause",paused?"yes":"no",nullptr};
    return active->api->command(active->player,cmd)>=0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvSeekStremio(
    JNIEnv*,jclass,jlong positionMs) {
    if(positionMs<0 || positionMs>24LL*60*60*1000)return false;
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="stremio" || !active || !active->ready.load())return false;
    std::lock_guard<std::mutex> pg(active->lock);
    if(!active->player)return false;
    const std::string seconds=std::to_string(positionMs/1000.0);
    const char* cmd[]={"seek",seconds.c_str(),"absolute",nullptr};
    return active->api->command(active->player,cmd)>=0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvSetTrackStremio(
    JNIEnv* env,jclass,jstring property,jstring value) {
    const auto p=asString(env,property),v=asString(env,value);
    if((p!="sid" && p!="aid") || v.size()>12 || v.empty())return false;
    if(v!="auto" && v!="no" && !std::all_of(v.begin(),v.end(),[](char c){return c>='0' && c<='9';}))return false;
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="stremio" || !active || !active->ready.load())return false;
    std::lock_guard<std::mutex> pg(active->lock);
    if(!active->player)return false;
    const char* cmd[]={"set",p.c_str(),v.c_str(),nullptr};
    return active->api->command(active->player,cmd)>=0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvAddSubtitleStremio(
    JNIEnv* env,jclass,jstring uri) {
    const std::string url=asString(env,uri);
    if(url.rfind("https://",0)!=0 || url.size()>4096 || url.find('\n')!=std::string::npos ||
       url.find('\r')!=std::string::npos)return false;
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="stremio" || !active || !active->ready.load())return false;
    std::lock_guard<std::mutex> pg(active->lock);
    if(!active->player)return false;
    const char* cmd[]={"sub-add",url.c_str(),"select",nullptr};
    return active->api->command(active->player,cmd)>=0;
}

// Live TV is the only owner allowed to change its broadcast audio/subtitle
// tracks. A separate Stremio MPV session must never receive T405 controls.
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvSetTrackForT405(
    JNIEnv* env,jclass,jstring property,jstring value) {
    const std::string p=asString(env,property), v=asString(env,value);
    if ((p!="sid" && p!="aid") || v.empty() || v.size()>12) return false;
    if (v!="auto" && v!="no" && !std::all_of(v.begin(),v.end(),[](char c){return c>='0'&&c<='9';})) return false;
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="livetv" || !active || !active->ready.load())return false;
    std::lock_guard<std::mutex> pg(active->lock);
    if(!active->player || !active->ready.load())return false;
    const char* cmd[]={"set",p.c_str(),v.c_str(),nullptr};
    return active->api->command(active->player,cmd)>=0;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_personalmedia_android_NativeBridge_mpvPauseForT405(
    JNIEnv*,jclass,jboolean paused) {
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="livetv" || !active || !active->ready.load())return false;
    std::lock_guard<std::mutex> pg(active->lock);
    if(!active->player || !active->ready.load())return false;
    const char* cmd[]={"set","pause",paused?"yes":"no",nullptr};
    return active->api->command(active->player,cmd)>=0;
}
extern "C" JNIEXPORT void JNICALL Java_com_personalmedia_android_NativeBridge_mpvRequestRedrawForT405(
    JNIEnv*,jclass) {
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner!="livetv" || !active)return;
    active->forceRedraw.store(true,std::memory_order_release);
    active->renderDirty.store(true,std::memory_order_release);
    active->cv.notify_all();
}
namespace {
std::string jsonQuoted(const std::string& value) {
    std::string result="\"";
    for(unsigned char c : value) {
        if(c=='"' || c=='\\') { result+='\\';result+=static_cast<char>(c); }
        else if(c<0x20) { char buf[7];std::snprintf(buf,sizeof(buf),"\\u%04x",static_cast<unsigned>(c));result+=buf; }
        else result+=static_cast<char>(c);
    }
    return result+'"';
}
std::string stringProperty(Worker& worker,const std::string& name) {
    // MPV_FORMAT_STRING = 1; libmpv allocates the resulting char*.
    if(!worker.api->getProperty || !worker.api->freeProperty)return {};
    char* value=nullptr;
    if(worker.api->getProperty(worker.player,name.c_str(),1,&value)<0 || !value)return {};
    std::string result(value);
    worker.api->freeProperty(value);
    return result;
}
int64_t intProperty(Worker& worker,const std::string& name,int64_t fallback=0) {
    if(!worker.api->getProperty)return fallback;
    int64_t value=0;
    return worker.api->getProperty(worker.player,name.c_str(),4,&value)>=0 ? value : fallback;
}
}
extern "C" JNIEXPORT jstring JNICALL Java_com_personalmedia_android_NativeBridge_mpvStatusStremio(
    JNIEnv* env,jclass) {
    std::string result="{\"active\":false,\"phase\":\"failed\",\"failureCode\":-15,\"audio\":[],\"subtitles\":[]}";
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner=="stremio" && active && active->ready.load()) {
        std::lock_guard<std::mutex> pg(active->lock);
        if(active->player && active->api->getProperty) {
            // A successful loadfile command merely queues playback. Read
            // libmpv's documented completion/failure events to avoid silently
            // showing a black screen with 00:00 for a rejected source.
            if(active->api->waitEvent) {
                for(int n=0;n<128;++n) {
                    const MpvEvent* e=active->api->waitEvent(active->player,0.0);
                    if(!e || e->event_id==0)break;
                    if(e->event_id==6) { // MPV_EVENT_START_FILE
                        active->mediaPhase="loading";
                        active->mediaFailure=0;
                    } else if(e->event_id==8) { // MPV_EVENT_FILE_LOADED
                        active->mediaPhase="ready";
                        active->mediaFailure=0;
                    } else if(e->event_id==7 && e->data) { // MPV_EVENT_END_FILE
                        const auto* ended=static_cast<const MpvEndFile*>(e->data);
                        if(ended->reason==4) { // MPV_END_FILE_REASON_ERROR
                            active->mediaPhase="failed";
                            active->mediaFailure=ended->error;
                        } else if(ended->reason==0) { // EOF
                            active->mediaPhase="ended";
                        } else if(ended->reason==5) { // playlist redirect
                            active->mediaPhase="loading";
                        }
                    }
                }
            }
            auto seconds=[&](const char* name)->double {
                double v=0;
                return active->api->getProperty(active->player,name,5,&v)>=0 &&
                       v>=0 && v<24.0*3600.0 ? v : 0;
            };
            auto flag=[&](const char* name)->bool {
                int v=0;
                return active->api->getProperty(active->player,name,3,&v)>=0 && v!=0;
            };
            const int64_t pos=static_cast<int64_t>(seconds("time-pos")*1000);
            const int64_t dur=static_cast<int64_t>(seconds("duration")*1000);
            std::string audio="[",subs="[";
            int videoTracks=0;
            const int64_t count=std::min<int64_t>(96,std::max<int64_t>(0,intProperty(*active,"track-list/count")));
            for(int64_t i=0;i<count;++i) {
                const std::string prefix="track-list/"+std::to_string(i)+"/";
                const std::string type=stringProperty(*active,prefix+"type");
                if(type=="video") { ++videoTracks; continue; }
                if(type!="audio" && type!="sub")continue;
                const int64_t id=intProperty(*active,prefix+"id",-1);
                if(id<0)continue;
                std::string& target=type=="audio" ? audio : subs;
                if(target.size()>1)target+=",";
                target+="{\"id\":"+std::to_string(id)+",\"lang\":"+
                    jsonQuoted(stringProperty(*active,prefix+"lang"))+
                    ",\"title\":"+jsonQuoted(stringProperty(*active,prefix+"title"))+"}";
            }
            audio+="]";subs+="]";
            const int64_t w=intProperty(*active,"video-params/w");
            const int64_t h=intProperty(*active,"video-params/h");
            result="{\"active\":true,\"phase\":"+jsonQuoted(active->mediaPhase)+
                ",\"failureCode\":"+std::to_string(active->mediaFailure)+
                ",\"videoTracks\":"+std::to_string(videoTracks)+
                ",\"videoWidth\":"+std::to_string(w)+
                ",\"videoHeight\":"+std::to_string(h)+
                ",\"buffering\":"+(flag("paused-for-cache")?"true":"false")+
                ",\"positionMs\":"+std::to_string(pos)+
                ",\"durationMs\":"+std::to_string(dur)+
                ",\"paused\":"+(flag("pause")?"true":"false")+
                ",\"seekable\":"+(flag("seekable")?"true":"false")+
                ",\"audio\":"+audio+",\"subtitles\":"+subs+
                ",\"aid\":"+jsonQuoted(stringProperty(*active,"aid"))+
                ",\"sid\":"+jsonQuoted(stringProperty(*active,"sid"))+"}";
        }
    }
    return env->NewStringUTF(result.c_str());
}

// Read actual libmpv track-list entries; do not invent broadcast subtitles.
// Track list may be empty until libmpv has parsed the active MPEG-TS PMT.
extern "C" JNIEXPORT jstring JNICALL Java_com_personalmedia_android_NativeBridge_mpvTracksForT405(JNIEnv* env,jclass) {
    std::string result="{\"ready\":false,\"audio\":[],\"subtitles\":[]}";
    std::lock_guard<std::mutex> guard(global_lock);
    if(activeOwner=="livetv" && active && active->ready.load()) {
        std::lock_guard<std::mutex> pg(active->lock);
        if(active->player && active->api->getProperty) {
            std::string audio="[", subs="[";
            const int64_t count=std::min<int64_t>(96,std::max<int64_t>(0,intProperty(*active,"track-list/count")));
            for(int64_t i=0;i<count;++i) {
                const std::string prefix="track-list/"+std::to_string(i)+"/";
                const std::string type=stringProperty(*active,prefix+"type");
                if(type!="audio" && type!="sub")continue;
                const int64_t id=intProperty(*active,prefix+"id",-1);
                if(id<0)continue;
                const std::string title=stringProperty(*active,prefix+"title");
                const std::string lang=stringProperty(*active,prefix+"lang");
                std::string& out=(type=="audio"?audio:subs);
                if(out.size()>1)out+=",";
                out+="{\"id\":"+std::to_string(id)+",\"lang\":"+jsonQuoted(lang)+",\"title\":"+jsonQuoted(title)+"}";
            }
            audio+="]";subs+="]";
            result="{\"ready\":true,\"audio\":"+audio+",\"subtitles\":"+subs+
                ",\"aid\":"+jsonQuoted(stringProperty(*active,"aid"))+
                ",\"sid\":"+jsonQuoted(stringProperty(*active,"sid"))+"}";
        }
    }
    return env->NewStringUTF(result.c_str());
}

// Diagnostic introspection; video dimensions >0 mean the decoder has reported
// video parameters, NOT that Android has necessarily presented the first frame.
extern "C" JNIEXPORT jstring JNICALL Java_com_personalmedia_android_NativeBridge_mpvLastError(JNIEnv* env,jclass) {
    return env->NewStringUTF(getMpvError().c_str());
}
extern "C" JNIEXPORT jstring JNICALL Java_com_personalmedia_android_NativeBridge_mpvDiagnostics(JNIEnv* env,jclass) {
    std::string detail = "MPV session not active";
    {
        std::lock_guard<std::mutex> guard(global_lock);
        if(active) {
            detail = active->ready.load() ? "EGL initialized; MPV loadfile command accepted" : "MPV initializing";
            std::lock_guard<std::mutex> pg(active->lock);
            if(active->player && active->api && active->api->getProperty) {
                int64_t width=0,height=0,vid=0,aid=0;
                // mpv MPV_FORMAT_INT64=4. Values may legitimately remain zero
                // while the tuner/player awaits a decodable keyframe.
                active->api->getProperty(active->player,"video-params/w",4,&width);
                active->api->getProperty(active->player,"video-params/h",4,&height);
                active->api->getProperty(active->player,"vid",4,&vid);
                active->api->getProperty(active->player,"aid",4,&aid);
                detail += " | video=" + std::to_string(width) + "x" + std::to_string(height)
                    + " | vid=" + std::to_string(vid) + " aid=" + std::to_string(aid);
            }
        }
    }
    const std::string error=getMpvError();
    if(!error.empty())detail += " | lastError=" + error;
    return env->NewStringUTF(detail.c_str());
}
