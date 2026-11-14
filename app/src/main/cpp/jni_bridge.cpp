#include <jni.h>
#include <string>
#include "portable/streaming_torrent_session.h"
#include "portable/torrent_media_type.h"
#include "portable/mpv_range_server.h"
#include "portable/native_live_tv.h"

namespace {
std::string str(JNIEnv* e, jstring s) {
    if (!s) return {};
    const char* c = e->GetStringUTFChars(s, nullptr);
    if (!c) return {};
    std::string result(c);
    e->ReleaseStringUTFChars(s, c);
    return result;
}
jstring js(JNIEnv* e, const std::string& s) { return e->NewStringUTF(s.c_str()); }
}
#define PM_FN(name) Java_com_personalmedia_android_NativeBridge_##name
extern "C" JNIEXPORT jboolean JNICALL PM_FN(engineAvailable)(JNIEnv*, jclass) {
    return pm_torrent::StreamingEngineAvailable();
}
extern "C" JNIEXPORT jstring JNICALL PM_FN(torrentOpen)(JNIEnv* e,jclass,jstring hash,jint index,jstring trackers,jstring cache) {
    auto r=pm_torrent::OpenStreamingTorrent(str(e,hash),index,str(e,trackers),str(e,cache));
    return js(e, (r.ready ? "ready: " : "pending: ")+r.status);
}
extern "C" JNIEXPORT jstring JNICALL PM_FN(torrentStatus)(JNIEnv* e,jclass) {
    auto s=pm_torrent::CurrentStreamingInfo();
    // Typed counters only: no tracker URLs, media paths or IPs in UI JSON.
    return js(e,"{\"active\":"+std::string(s.active?"true":"false")+
       ",\"metadata\":"+(s.metadata?"true":"false")+
       ",\"fileIndex\":"+std::to_string(s.selectedFile)+
       ",\"peers\":"+std::to_string(s.peers)+
       ",\"seeds\":"+std::to_string(s.seeds)+
       ",\"downloadBps\":"+std::to_string(s.downBytesPerSecond)+
       ",\"verifiedAhead\":"+std::to_string(s.verifiedAhead)+
       ",\"verifiedTail\":"+std::to_string(s.verifiedTail)+
       ",\"trackerReplies\":"+std::to_string(s.trackerReplies)+
       ",\"dhtNodes\":"+std::to_string(s.dhtNodes)+
       ",\"size\":"+std::to_string(s.selectedSize)+"}");
}
extern "C" JNIEXPORT jstring JNICALL PM_FN(torrentRangeUrl)(JNIEnv* e,jclass,jstring hash,jint index) {
    if (!pm_torrent::StreamingEngineAvailable()) return js(e,"");
    const auto status=pm_torrent::CurrentStreamingInfo();
    if(!status.active || !status.metadata || status.selectedSize==0)return js(e,"");
    const int selected=pm_torrent::ResolveStreamingFileIndex(index,status.selectedFile);
    if(selected<0)return js(e,"");
    auto r=pm_torrent::StartMpvRangeServer(str(e,hash),selected);
    return js(e,r.ready ? r.url : "");
}
extern "C" JNIEXPORT void JNICALL PM_FN(torrentRequestCancel)(JNIEnv*,jclass) {
    pm_torrent::RequestStreamingTorrentCancel();
}
extern "C" JNIEXPORT void JNICALL PM_FN(torrentDemand)(JNIEnv*,jclass,jlong offset,jint length) {
    if(offset>=0 && length>0 && length<=2*1024*1024)
        pm_torrent::DemandStreamingRange(static_cast<uint64_t>(offset),
            static_cast<uint32_t>(length),0);
}
extern "C" JNIEXPORT jbyteArray JNICALL PM_FN(torrentReadVerified)(JNIEnv* e,jclass,jlong offset,jint length) {
    if(offset<0||length<=0||length>256*1024)return nullptr;
    std::vector<uint8_t> bytes;
    if(!pm_torrent::ReadStreamingVerified(static_cast<uint64_t>(offset),
          static_cast<uint32_t>(length),bytes) || bytes.size()!=static_cast<size_t>(length))return nullptr;
    auto out=e->NewByteArray(length);
    if(out)e->SetByteArrayRegion(out,0,length,reinterpret_cast<const jbyte*>(bytes.data()));
    return out;
}
extern "C" JNIEXPORT void JNICALL PM_FN(torrentStop)(JNIEnv*,jclass) {
    pm_torrent::RequestStreamingTorrentCancel();
    pm_torrent::StopMpvRangeServer();
    pm_torrent::CloseStreamingTorrentAsync();
}
extern "C" JNIEXPORT jboolean JNICALL PM_FN(tvConnect)(JNIEnv* e,jclass,jstring host,jint cp,jint rp) {
    return pm_live_tv::Connect(str(e,host),cp,rp);
}
extern "C" JNIEXPORT jint JNICALL PM_FN(tvControlSocketFd)(JNIEnv*,jclass) { return pm_live_tv::ControlSocketFd(); }
extern "C" JNIEXPORT void JNICALL PM_FN(tvDisconnect)(JNIEnv*,jclass) { pm_live_tv::Disconnect(); }
extern "C" JNIEXPORT jboolean JNICALL PM_FN(tvProbe)(JNIEnv*,jclass) {return pm_live_tv::Probe();}
extern "C" JNIEXPORT jboolean JNICALL PM_FN(tvTune)(JNIEnv*,jclass,jint hz,jint bw) {return pm_live_tv::Tune(hz,bw);}
extern "C" JNIEXPORT jboolean JNICALL PM_FN(tvScan)(JNIEnv* e,jclass,jstring profile) {return pm_live_tv::Scan(str(e,profile));}
extern "C" JNIEXPORT void JNICALL PM_FN(tvCancelScan)(JNIEnv*,jclass) {pm_live_tv::CancelScan();}
extern "C" JNIEXPORT jstring JNICALL PM_FN(tvStatus)(JNIEnv* e,jclass) {return js(e,pm_live_tv::StatusJson());}
extern "C" JNIEXPORT jstring JNICALL PM_FN(tvChannels)(JNIEnv* e,jclass) {return js(e,pm_live_tv::ChannelsJson());}
extern "C" JNIEXPORT jint JNICALL PM_FN(tvRestoreChannels)(JNIEnv* e,jclass,jstring data) {return pm_live_tv::RestoreChannels(str(e,data));}
extern "C" JNIEXPORT jstring JNICALL PM_FN(tvPlay)(JNIEnv* e,jclass,jstring key) {return js(e,pm_live_tv::Play(str(e,key)));}
extern "C" JNIEXPORT void JNICALL PM_FN(tvStop)(JNIEnv*,jclass) {pm_live_tv::StopPlay();}

extern "C" JNIEXPORT jboolean JNICALL PM_FN(tvRecoverStream)(JNIEnv*,jclass) {return pm_live_tv::RecoverStream();}
