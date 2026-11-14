#include "matroska_mp4.h"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <limits>
#include <numeric>
#include <sstream>

namespace pm_torrent {
namespace {
constexpr size_t kMaxMediaInputBytes = 64 * 1024 * 1024;
constexpr size_t MAX_OUTPUT = 72 * 1024 * 1024;
constexpr size_t MAX_SAMPLES = 9000;
constexpr uint64_t SEGMENT = 0x18538067, TRACKS = 0x1654AE6B;
constexpr uint64_t CLUSTER = 0x1F43B675, SIMPLEBLOCK=0xA3, BLOCKGROUP=0xA0;
struct Element { uint64_t id=0; size_t start=0,end=0; bool unknown=false; };
struct Track {
    uint64_t number=0, kind=0;
    uint32_t width=0,height=0,channels=0,rate=0;
    std::string codec;
    std::vector<uint8_t> privateData;
    bool encoded=false;
};
struct Sample { uint64_t pts=0; bool key=false; std::vector<uint8_t> bytes; };
struct TrackOutput { Track track; std::vector<Sample> samples; uint32_t step=40; uint32_t duration=0; uint32_t chunkOffset=0; uint32_t compositionShift=0; };
// video timescale = 1000 ticks/sec. AAC timescale = actual sample rate,
// so each AAC-LC packet is 1024 ticks and never accumulates integer-ms drift.
uint32_t movieDuration(const TrackOutput &t, bool isVideo) {
    if (isVideo) return t.duration;
    return uint32_t((uint64_t(t.duration) * 1000ull + t.track.rate/2) / t.track.rate);
}

bool vint(const uint8_t* d, size_t end, size_t &pos, int max, bool isSize, uint64_t &value, bool &unknown) {
    if(pos>=end || !d[pos]) return false;
    const uint8_t first=d[pos]; int n=1; uint8_t mask=0x80;
    while(n<=max && !(first&mask)) {++n; mask>>=1;}
    if(n>max || n>int(end-pos))return false;
    value=isSize?uint64_t(first&(mask-1)):uint64_t(first);
    for(int i=1;i<n;i++)value=(value<<8)|d[pos+i];
    pos+=n;
    unknown=isSize && value==((uint64_t(1)<<(7*n))-1);
    return true;
}
bool element(const uint8_t* d,size_t limit,size_t &pos,Element &out) {
    uint64_t id=0,len=0;bool u=false;
    if(!vint(d,limit,pos,4,false,id,u) || !vint(d,limit,pos,8,true,len,u))return false;
    out.id=id;out.start=pos;out.unknown=u;
    if(u || len>limit-pos) {
        // Only Segment and Cluster are allowed to extend beyond our verified prefix.
        if(id!=SEGMENT && id!=CLUSTER)return false;
        out.end=limit;
    } else out.end=pos+size_t(len);
    pos=out.end;
    return true;
}
uint64_t integer(const uint8_t* d,const Element &e) {
    if(e.end<=e.start || e.end-e.start>8)return 0;
    uint64_t value=0;
    for(size_t i=e.start;i<e.end;i++)value=(value<<8)|d[i];
    return value;
}
std::string ascii(const uint8_t* d,const Element&e){
    if(e.end<=e.start || e.end-e.start>60)return {};
    for(size_t i=e.start;i<e.end;i++)if(d[i]<32||d[i]>126)return {};
    return std::string(reinterpret_cast<const char*>(d+e.start),e.end-e.start);
}
uint32_t sampleRate(const uint8_t* d,const Element &e){
    double v=0;
    if(e.end-e.start==4){uint32_t bits=uint32_t(integer(d,e));float f=0;std::memcpy(&f,&bits,4);v=f;}
    else if(e.end-e.start==8){uint64_t bits=integer(d,e);std::memcpy(&v,&bits,8);}
    if(!std::isfinite(v) || v<8000 || v>192000)return 0;
    return uint32_t(std::lround(v));
}
void parseTrack(const uint8_t*d,const Element&entry,Track &t) {
    size_t p=entry.start;
    while(p<entry.end){Element e;if(!element(d,entry.end,p,e)||e.unknown)break;
        if(e.id==0xD7)t.number=integer(d,e);
        else if(e.id==0x83)t.kind=integer(d,e);
        else if(e.id==0x86)t.codec=ascii(d,e);
        else if(e.id==0x63A2 && e.end-e.start<=8192)t.privateData.assign(d+e.start,d+e.end);
        else if(e.id==0x6D80)t.encoded=true;
        else if(e.id==0xE0 || e.id==0xE1) {
            size_t sub=e.start;
            while(sub<e.end){Element c;if(!element(d,e.end,sub,c)||c.unknown)break;
                if(c.id==0xB0)t.width=uint32_t(integer(d,c));
                if(c.id==0xBA)t.height=uint32_t(integer(d,c));
                if(c.id==0x9F)t.channels=uint32_t(integer(d,c));
                if(c.id==0xB5)t.rate=sampleRate(d,c);
            }
        }
    }
}
void tracks(const uint8_t*d,const Element&root,std::vector<Track>&list){
    size_t p=root.start;
    while(p<root.end&&list.size()<16){Element e;if(!element(d,root.end,p,e)||e.unknown)break;
        if(e.id==0xAE){Track t;parseTrack(d,e,t);if(t.number)list.push_back(std::move(t));}
    }
}
// Only *unlaced* SimpleBlocks in this phase. Refuse corrupted/laced payloads,
// never interpret them as playable video or audio samples.
void block(const uint8_t*d,const Element&b,uint64_t clusterTime,uint64_t scale,
           const Track&video,const Track&audio,TrackOutput&v,TrackOutput&a,
           uint64_t absoluteTargetMs){
    size_t p=b.start;bool unknown=false;uint64_t trackNumber=0;
    if(!vint(d,b.end,p,8,true,trackNumber,unknown)||p+3>=b.end)return;
    const int16_t rel=static_cast<int16_t>((uint16_t(d[p])<<8)|d[p+1]);
    const uint8_t flags=d[p+2];p+=3;
    if((flags&0x06)!=0 || (trackNumber!=video.number&&trackNumber!=audio.number) ||
       b.end-p>512*1024 || b.end==p || clusterTime>INT64_MAX / 1000000000)return;
    const int64_t ticks=int64_t(clusterTime)+rel;
    if(ticks>0 && uint64_t(ticks)>UINT64_MAX/scale)return;
    const int64_t ms=ticks>=0 ? int64_t(uint64_t(ticks)*scale/1000000ull) :
      -int64_t(uint64_t(-ticks)*scale/1000000ull);
    // A negative initial AAC priming PTS is representable as zero in the
    // bounded preview; full A/V sync and edit lists are future work.
    const uint64_t pts=uint64_t(std::max<int64_t>(0,ms));
    // C.17.10: source-time-directed packet collection. The MKV parser must
    // still visit the complete verified clusters, but need not store thousands
    // of earlier AAC packets and reach MAX_SAMPLES before the wanted keyframe.
    // Keep a small preroll; later trimming still demands an independent HEVC
    // IRAP and AAC aligned to the chosen timestamp.
    if (absoluteTargetMs > 1200 && pts < absoluteTargetMs - 1200) return;
    TrackOutput *out=trackNumber==video.number?&v:&a;
    if(out->samples.size()>=MAX_SAMPLES)return;
    Sample sample;sample.pts=pts;
    sample.key=trackNumber==video.number?(flags&0x80)!=0:true;
    sample.bytes.assign(d+p,d+b.end);
    out->samples.emplace_back(std::move(sample));
}
void cluster(const uint8_t*d,const Element&root,uint64_t scale,
             const Track&video,const Track&audio,TrackOutput&v,TrackOutput&a,
           uint64_t absoluteTargetMs){
    size_t p=root.start;uint64_t clusterTime=0;
    while(p<root.end && v.samples.size()+a.samples.size()<MAX_SAMPLES){
        Element e;if(!element(d,root.end,p,e)||e.unknown)break;
        if(e.id==0xE7)clusterTime=integer(d,e);
        else if(e.id==SIMPLEBLOCK)block(d,e,clusterTime,scale,video,audio,v,a,absoluteTargetMs);
        else if(e.id==BLOCKGROUP){
            // BlockGroup reference/dependencies need richer keyframe handling.
            // Do not silently mark unproven frames as independently decodable.
        }
    }
}
using Bytes=std::vector<uint8_t>;
void u8(Bytes&b,uint8_t x){b.push_back(x);}
void u16(Bytes&b,uint32_t x){u8(b,uint8_t(x>>8));u8(b,uint8_t(x));}
void u24(Bytes&b,uint32_t x){u8(b,uint8_t(x>>16));u16(b,x);}
void u32(Bytes&b,uint32_t x){u16(b,x>>16);u16(b,x);}
void str(Bytes&b,const char *s){for(int i=0;i<4;i++)u8(b,uint8_t(s[i]));}
size_t box(Bytes&b,const char*s){size_t start=b.size();u32(b,0);str(b,s);return start;}
void close(Bytes&b,size_t start){uint64_t length=b.size()-start;if(length>UINT32_MAX)throw 1;
    const uint32_t n=uint32_t(length);for(int i=0;i<4;i++)b[start+i]=uint8_t(n>>(24-8*i));}
void zeros(Bytes&b,size_t n){b.insert(b.end(),n,0);}
void matrix(Bytes&b){u32(b,0x10000);u32(b,0);u32(b,0);u32(b,0);u32(b,0x10000);u32(b,0);u32(b,0);u32(b,0);u32(b,0x40000000);}
void descriptorLength(Bytes&b,uint32_t n){
    uint8_t parts[4];int num=0;
    do {parts[num++]=uint8_t(n&127);n>>=7;}while(n && num<4);
    for(int i=num-1;i>=0;i--)u8(b,parts[i]|(i?0x80:0));
}
void descriptor(Bytes&b,uint8_t tag,const Bytes&content){u8(b,tag);descriptorLength(b,uint32_t(content.size()));b.insert(b.end(),content.begin(),content.end());}
void esds(Bytes&b,const Track&t){
    const size_t start=box(b,"esds");u32(b,0);
    Bytes decoder;
    u8(decoder,0x40);u8(decoder,0x15);u24(decoder,0);u32(decoder,0);u32(decoder,0);
    descriptor(decoder,0x05,t.privateData);
    Bytes es;u16(es,2);u8(es,0);descriptor(es,0x04,decoder);
    descriptor(es,0x06,Bytes{2});
    descriptor(b,0x03,es);
    close(b,start);
}
void stsd(Bytes&b,const TrackOutput&t,bool isVideo){
    size_t sd=box(b,"stsd");u32(b,0);u32(b,1);
    if(isVideo){
        size_t sample=box(b,"hvc1");zeros(b,6);u16(b,1);zeros(b,16);
        u16(b,t.track.width);u16(b,t.track.height);
        u32(b,0x00480000);u32(b,0x00480000);u32(b,0);u16(b,1);
        zeros(b,32);u16(b,24);u16(b,0xffff);
        size_t hvcc=box(b,"hvcC");b.insert(b.end(),t.track.privateData.begin(),t.track.privateData.end());close(b,hvcc);
        close(b,sample);
    }else{
        size_t sample=box(b,"mp4a");zeros(b,6);u16(b,1);zeros(b,8);
        u16(b,t.track.channels);u16(b,16);u16(b,0);u16(b,0);
        u32(b,t.track.rate<<16);esds(b,t.track);close(b,sample);
    }
    close(b,sd);
}
void sampleTable(Bytes&b,const TrackOutput&t,bool isVideo){
    const size_t stbl=box(b,"stbl");stsd(b,t,isVideo);
    size_t stts=box(b,"stts");u32(b,0);u32(b,1);u32(b,uint32_t(t.samples.size()));u32(b,t.step);close(b,stts);
    if(isVideo){
        // Matroska packet order is decode order; PTS comes from Cluster+Block.
        // Constant-DTS approximation allows a short compatibility test; NOT
        // accurate seeking, B-frame timestamps or full programme playback.
        size_t ct=box(b,"ctts");u32(b,0);
        u32(b,uint32_t(t.samples.size()));
        for(size_t i=0;i<t.samples.size();i++){
            u32(b,1);
            const int64_t pts=int64_t(t.samples[i].pts)-int64_t(t.samples.front().pts);
            const int64_t delta=pts-int64_t(i*uint64_t(t.step));
            u32(b,uint32_t(std::max<int64_t>(0,delta+int64_t(t.compositionShift))));
        }
        close(b,ct);
    }
    size_t sc=box(b,"stsc");u32(b,0);u32(b,1);u32(b,1);u32(b,uint32_t(t.samples.size()));u32(b,1);close(b,sc);
    size_t sz=box(b,"stsz");u32(b,0);u32(b,0);u32(b,uint32_t(t.samples.size()));
    for(const auto& s:t.samples){u32(b,uint32_t(s.bytes.size()));}close(b,sz);
    size_t co=box(b,"stco");u32(b,0);u32(b,1);u32(b,t.chunkOffset);close(b,co);
    if(isVideo){
        uint32_t keyCount=0;for(const auto& s:t.samples)if(s.key)keyCount++;
        size_t ss=box(b,"stss");u32(b,0);u32(b,keyCount);
        for(size_t i=0;i<t.samples.size();i++)if(t.samples[i].key)u32(b,uint32_t(i+1));
        close(b,ss);
    }
    close(b,stbl);
}
void trak(Bytes&b,const TrackOutput&t,uint32_t id,bool isVideo){
    size_t tr=box(b,"trak");
    size_t tk=box(b,"tkhd");u32(b,7);u32(b,0);u32(b,0);u32(b,id);u32(b,0);u32(b,movieDuration(t,isVideo));
    zeros(b,8);u16(b,0);u16(b,0);u16(b,isVideo?0:0x100);u16(b,0);
    matrix(b);u32(b,isVideo?t.track.width<<16:0);u32(b,isVideo?t.track.height<<16:0);close(b,tk);
    size_t md=box(b,"mdia");
    size_t mh=box(b,"mdhd");u32(b,0);u32(b,0);u32(b,0);u32(b,isVideo?1000:t.track.rate);u32(b,t.duration);u16(b,0x55C4);u16(b,0);close(b,mh);
    size_t hd=box(b,"hdlr");u32(b,0);u32(b,0);str(b,isVideo?"vide":"soun");zeros(b,12);
    const char* name=isVideo?"PersonalMedia HEVC":"PersonalMedia AAC";
    for(const char*p=name;*p;p++){u8(b,uint8_t(*p));}u8(b,0);close(b,hd);
    size_t mi=box(b,"minf");
    if(isVideo){size_t vm=box(b,"vmhd");u32(b,1);zeros(b,8);close(b,vm);}
    else{size_t sm=box(b,"smhd");u32(b,0);zeros(b,4);close(b,sm);}
    size_t di=box(b,"dinf");size_t dr=box(b,"dref");u32(b,0);u32(b,1);
    size_t url=box(b,"url ");u32(b,1);close(b,url);close(b,dr);close(b,di);
    sampleTable(b,t,isVideo);close(b,mi);close(b,md);close(b,tr);
}
void makeMp4(TrackOutput&v,TrackOutput&a,Bytes &out){
    size_t ft=box(out,"ftyp");str(out,"isom");u32(out,0x200);str(out,"isom");str(out,"iso6");str(out,"mp41");str(out,"hvc1");close(out,ft);
    size_t md=box(out,"mdat");
    v.chunkOffset=uint32_t(out.size());
    for(const auto&s:v.samples)out.insert(out.end(),s.bytes.begin(),s.bytes.end());
    a.chunkOffset=uint32_t(out.size());
    for(const auto&s:a.samples)out.insert(out.end(),s.bytes.begin(),s.bytes.end());
    close(out,md);
    size_t mo=box(out,"moov");
    size_t mv=box(out,"mvhd");u32(out,0);u32(out,0);u32(out,0);u32(out,1000);
    u32(out,std::max(movieDuration(v,true),movieDuration(a,false)));u32(out,0x10000);u16(out,0x100);u16(out,0);
    zeros(out,8);matrix(out);zeros(out,24);u32(out,3);close(out,mv);
    trak(out,v,1,true);trak(out,a,2,false);close(out,mo);
}
bool validateTrack(const Track &v,const Track &a,std::string &issue){
    if(v.number==0||a.number==0){issue="Both HEVC video and AAC audio tracks are required";return false;}
    if(v.encoded||a.encoded){issue="Matroska ContentEncodings unsupported (encrypted/compressed track)";return false;}
    if(v.width==0||v.height==0||v.width>8192||v.height>8192 || a.channels==0||a.channels>8 || a.rate<8000||a.rate>65535){issue="Unsupported MKV dimensions or AAC channel/sample-rate metadata";return false;}
    if(v.privateData.size()<23 || v.privateData.size()>4096 || v.privateData[0]!=1 ||
       (v.privateData[21]&3)!=3){issue="HEVC hvcC decoder configuration missing or NAL-length size is not 4";return false;}
    if(a.privateData.size()<2 || a.privateData.size()>64){issue="AAC AudioSpecificConfig missing or oversized";return false;}
    return true;
}
uint32_t determineVideoStep(const TrackOutput &v){
    std::vector<uint64_t> times;
    for(const auto&s:v.samples)times.push_back(s.pts);
    std::sort(times.begin(),times.end());
    std::vector<uint64_t> deltas;
    for(size_t i=1;i<times.size();i++){
        uint64_t diff=times[i]-times[i-1];
        if(diff>=10&&diff<=200)deltas.push_back(diff);
    }
    if(deltas.empty())return 40;
    std::sort(deltas.begin(),deltas.end());
    return uint32_t(std::max<uint64_t>(10,std::min<uint64_t>(200,deltas[deltas.size()/2])));
}
bool hevcRandomAccessSample(const Sample &sample){
    size_t p=0;
    while(p+4<=sample.bytes.size()){
        const uint32_t count=(uint32_t(sample.bytes[p])<<24)|
             (uint32_t(sample.bytes[p+1])<<16)|(uint32_t(sample.bytes[p+2])<<8)|sample.bytes[p+3];
        p+=4;
        if(count<2||count>sample.bytes.size()-p)return false;
        const uint8_t nalType=(sample.bytes[p]>>1)&0x3f;
        if(nalType>=16 && nalType<=21)return true; // HEVC IRAP incl. IDR/CRA
        p+=count;
    }
    return false;
}
bool hevcLengthPrefixed(const Sample &s){
    size_t p=0;int units=0;
    while(p+4<=s.bytes.size()){
        const uint32_t n=(uint32_t(s.bytes[p])<<24)|(uint32_t(s.bytes[p+1])<<16)|
              (uint32_t(s.bytes[p+2])<<8)|s.bytes[p+3];
        p+=4;if(n<2 || n>s.bytes.size()-p)return false;
        p+=n;units++;
    }
    return units>0 && p==s.bytes.size();
}
} // namespace
MkvMp4Preview RemuxVerifiedMkvPrefix(const uint8_t*data,size_t n,uint32_t previewMs,uint32_t segmentStartMs,bool laterWindow,uint64_t absoluteTargetMs){
    MkvMp4Preview result;
    if(previewMs!=3500 && previewMs!=15000 && previewMs!=45000){result.diagnostic="Preview duration must be 3.5, 15 or 45 seconds";return result;}
    if(absoluteTargetMs>86400000 || (absoluteTargetMs!=0 && (!laterWindow || previewMs!=15000))) {
        result.diagnostic="Invalid source-time target for verified later MP4";return result;
    }
    if(absoluteTargetMs==0 && segmentStartMs != 0 && (previewMs != 15000 || segmentStartMs % 15000 != 0 ||
        segmentStartMs > (laterWindow ? 105000u : 75000u))) {
        result.diagnostic="Requested segment start is beyond its verified rolling-window limit";
        return result;
    }
    if(!data||n<1024||n>kMaxMediaInputBytes){result.diagnostic="Need 1 KiB–64 MiB of contiguous verified opening MKV bytes";return result;}
    try {
        size_t pos=0;Element e;if(!element(data,n,pos,e)||e.id!=0x1A45DFA3){result.diagnostic="Not an MKV EBML header";return result;}
        Element segment;bool found=false;
        while(pos<n){if(!element(data,n,pos,e))break;if(e.id==SEGMENT){segment=e;found=true;break;}}
        if(!found){result.diagnostic="No Matroska Segment in verified prefix";return result;}
        std::vector<Track> list;uint64_t scale=1000000;
        // First pass: track table and TimecodeScale (normally before clusters).
        size_t p=segment.start;
        while(p<segment.end){Element part;if(!element(data,segment.end,p,part))break;
            if(part.id==TRACKS)tracks(data,part,list);
            else if(part.id==0x1549A966){size_t q=part.start;while(q<part.end){Element sub;if(!element(data,part.end,q,sub))break;
                    if(sub.id==0x2AD7B1)scale=integer(data,sub);
                }}
        }
        if(scale<1000 || scale>1000000000ull){result.diagnostic="Unsupported Matroska timecode scale";return result;}
        Track video,audio;
        for(const auto&t:list){
            if(!video.number && t.kind==1 && t.codec=="V_MPEGH/ISO/HEVC")video=t;
            if(!audio.number && t.kind==2 && t.codec=="A_AAC")audio=t;
        }
        if(!validateTrack(video,audio,result.diagnostic))return result;
        TrackOutput v,a;v.track=video;a.track=audio;
        // Second pass: collect *complete*, unlaced packets from verified bytes only.
        p=segment.start;
        while(p<segment.end && v.samples.size()+a.samples.size()<MAX_SAMPLES){
            Element part;if(!element(data,segment.end,p,part))break;
            if(part.id==CLUSTER)cluster(data,part,scale,video,audio,v,a,absoluteTargetMs);
        }
        // Later verified windows may open mid-GOP. This mode is opt-in and
        // trims to the first Matroska keyframe flag, with aligned AAC; older
        // opening/rolling preview paths remain untouched.
        if(laterWindow && !v.samples.empty()) {
            auto key=std::find_if(v.samples.begin(),v.samples.end(),
                [](const Sample& sample){return sample.key;});
            if(key!=v.samples.end()) {
                const uint64_t keyMs=key->pts;
                v.samples.erase(v.samples.begin(),key);
                auto firstAudio=std::lower_bound(a.samples.begin(),a.samples.end(),keyMs,
                    [](const Sample &sample,uint64_t ms){return sample.pts<ms;});
                a.samples.erase(a.samples.begin(),firstAudio);
            }
        }
        if(v.samples.size()<8||a.samples.size()<8||!v.samples.front().key){
            result.diagnostic="Too few complete unlaced HEVC/AAC packets or opening video is not a keyframe; buffer opening or use a different source";return result;
        }
        // C.10: choose a true sync frame for the second independent clip.
        // Audio follows the same source timestamp; it cannot be taken from
        // the start of the movie or the next clip would repeat old sound.
        // This is a keyframe-aligned independent MP4, NOT gapless playback.
        const uint64_t beginning=v.samples.front().pts;
        if(segmentStartMs != 0 || absoluteTargetMs!=0) {
            const uint64_t target=absoluteTargetMs!=0 ? absoluteTargetMs : beginning+segmentStartMs;
            auto key=std::find_if(v.samples.begin(),v.samples.end(),[target](const Sample &s){
                return s.key && s.pts >= target;
            });
            if(key==v.samples.end() || key->pts > target+10000) {
                result.diagnostic="C.17.10: no verified HEVC sync sample within 10s after target " +
                  std::to_string(target) + "ms; scanned " + std::to_string(v.samples.size()) +
                  " target-filtered video packets and " + std::to_string(a.samples.size()) +
                  " AAC packets";
                return result;
            }
            const uint64_t keyPts=key->pts;
            v.samples.erase(v.samples.begin(),key);
            auto firstAudio=std::lower_bound(a.samples.begin(),a.samples.end(),keyPts,
                [](const Sample &s,uint64_t pts){return s.pts<pts;});
            // AAC is usually in timestamp order; refuse invalid ordering
            // instead of generating a mismatched second soundtrack.
            if(firstAudio==a.samples.end() || firstAudio->pts>keyPts+1000) {
                result.diagnostic="C.12: no aligned AAC packets after the chosen segment keyframe";
                return result;
            }
            a.samples.erase(a.samples.begin(),firstAudio);
        }
        // Stop at the last complete samples within this clip's time window.
        const uint64_t first=v.samples.front().pts;
        const uint64_t end=first+previewMs;
        while(!v.samples.empty() && v.samples.back().pts>end)v.samples.pop_back();
        while(!a.samples.empty() && a.samples.back().pts>end)a.samples.pop_back();
        if(v.samples.size()<8||a.samples.size()<8){result.diagnostic="Not enough verified packets for a short A/V sample";return result;}
        if(laterWindow && (v.samples.back().pts<first+13500 ||
                           a.samples.back().pts<first+13500)) {
            result.diagnostic="C.17.10: verified later window insufficient post-keyframe media at " +
               std::to_string(first) + "ms; available HEVC until " +
               std::to_string(v.samples.back().pts) + "ms and AAC until " +
               std::to_string(a.samples.back().pts) + "ms; need 13.5 seconds";
            return result;
        }
        if(laterWindow && !hevcRandomAccessSample(v.samples.front())) {
            result.diagnostic="C.16: later keyframe flag lacks a verified HEVC IRAP NAL; refusing non-independent starting picture";
            return result;
        }
        // Do not declare a 45-second preview ready when only a 15-second
        // prefix is cached. Require complete, correctly ordered A/V sample
        // timestamps near the target duration; a missing cache range cannot
        // silently truncate the expected output.
        if((segmentStartMs!=0 || absoluteTargetMs!=0) && (v.samples.back().pts < first+13500 ||
            a.samples.back().pts < first+13500)) {
            result.diagnostic="C.12: verified MKV prefix lacks 13.5 seconds after requested keyframe; extend opening cache";
            return result;
        }
        if(previewMs==45000 && (v.samples.back().pts < first+40000 ||
           a.samples.back().pts < first+40000)) {
            result.diagnostic="C.9: verified MKV prefix holds less than 40 seconds of A/V packets; extend opening cache toward 64 MiB and retry";
            return result;
        }
        for(const auto&s:v.samples)if(!hevcLengthPrefixed(s)){result.diagnostic="HEVC packets are not complete 4-byte NAL-length samples";return result;}
        v.step=determineVideoStep(v);
        // AAC-LC frame = precisely 1024 samples in its own track timescale.
        // Still not a full source-timestamp/sync implementation.
        a.step=1024;
        v.duration=uint32_t(v.samples.size())*v.step;
        a.duration=uint32_t(a.samples.size())*a.step;
        int64_t minOffset=0;
        for(size_t i=0;i<v.samples.size();i++){
            int64_t offset=int64_t(v.samples[i].pts)-int64_t(v.samples.front().pts)-int64_t(i*uint64_t(v.step));
            minOffset=std::min(minOffset,offset);
        }
        v.compositionShift=uint32_t(-minOffset);
        makeMp4(v,a,result.mp4);
        if(result.mp4.size()>MAX_OUTPUT){result.mp4.clear();result.diagnostic="MP4 preview exceeded 72 MiB safety limit";return result;}
        result.sourceStartMs=first;
        result.ok=true;result.videoSamples=uint32_t(v.samples.size());result.audioSamples=uint32_t(a.samples.size());
        result.durationMs=std::max(movieDuration(v,true),movieDuration(a,false));
        result.diagnostic=segmentStartMs!=0 ?
          "C.12 independent MP4 begins at a HEVC keyframe at/after the requested source position. Precise boundaries and full-movie seeking are NOT implemented." :
          "Bounded HEVC/AAC MP4 preview built with exact AAC clock; video DTS/composition timing remains approximate. Not continuous playback.";
    }catch(...){result.mp4.clear();result.diagnostic="Malformed or unsupported Matroska packet/MP4 size";}
    return result;
}
}
