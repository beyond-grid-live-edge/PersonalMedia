#include "matroska_cues.h"
#include "matroska_probe.h"
#include "matroska_timeline.h"
#include <algorithm>
#include <limits>

namespace pm_torrent {
namespace {
constexpr uint64_t EBML=0x1a45dfa3, SEGMENT=0x18538067, SEEKHEAD=0x114d9b74,
    SEEK=0x4dbb, SEEKID=0x53ab, SEEKPOSITION=0x53ac, CUES=0x1c53bb6b,
    POINT=0xbb, TIME=0xb3, POSITIONS=0xb7, TRACK=0xf7, CLUSTERPOS=0xf1,
    CLUSTER=0x1f43b675, CLUSTERTIME=0xe7;
constexpr size_t kMaxCueBytes=4u*1024u*1024u;
struct El {uint64_t id=0;size_t start=0,data=0,end=0;bool partial=false;};
bool vint(const uint8_t*d,size_t end,size_t&at,unsigned limit,bool sz,
          uint64_t&value,bool&unknown){
    if(at>=end||!d[at])return false;
    const uint8_t byte=d[at];unsigned mark=0x80, count=1;
    while(count<=limit && !(byte&mark)){mark>>=1;++count;}
    if(count>limit||count>end-at)return false;
    value=sz?(byte&(mark-1)):byte;
    for(unsigned i=1;i<count;++i)value=(value<<8)|d[at+i];
    unknown=sz && value==((uint64_t(1)<<(7*count))-1);
    at+=count;return true;
}
bool elem(const uint8_t*d,size_t end,size_t&at,El&v,bool partialSegment=false){
    v={};v.start=at;uint64_t length=0;bool unused=false, unknown=false;
    if(!vint(d,end,at,4,false,v.id,unused)||!vint(d,end,at,8,true,length,unknown))return false;
    v.data=at;
    if(unknown||length>end-at){
        if(!partialSegment || v.id!=SEGMENT)return false;
        v.partial=true;v.end=end;
    }else v.end=at+size_t(length);
    at=v.end;return true;
}
bool num(const uint8_t*d,const El&el,uint64_t&v){
    if(el.end<=el.data||el.end-el.data>8||el.partial)return false;
    v=0;for(size_t i=el.data;i<el.end;++i)v=(v<<8)|d[i];return true;
}
bool sum(uint64_t a,uint64_t b,uint64_t&v){
    if(b>std::numeric_limits<uint64_t>::max()-a)return false;
    v=a+b;return true;
}
bool ms(uint64_t ticks,uint64_t scale,uint64_t&value){
    if(scale==0||scale>1000000000ULL||ticks>std::numeric_limits<uint64_t>::max()/scale)return false;
    value=ticks*scale/1000000ULL;return true;
}
uint32_t percentFor(uint64_t pos,uint64_t size){
    return size?uint32_t(std::min<uint64_t>(99,(pos/size)*100+((pos%size)*100)/size)):0;
}
struct Header {bool ok=false;uint64_t segmentStart=0,cueByte=0,scale=1000000,videoTrack=0;bool cueFound=false;std::string note;};
Header header(const uint8_t*d,size_t size,uint64_t fileSize){
    Header h;
    if(!d||size<64||size>2*1024*1024){h.note="No verified MKV opening header";return h;}
    size_t p=0;El e;
    if(!elem(d,size,p,e)||e.id!=EBML){h.note="Invalid verified EBML header";return h;}
    bool gotSeg=false;
    while(p<size){if(!elem(d,size,p,e,true))break;if(e.id==SEGMENT){gotSeg=true;break;}}
    if(!gotSeg){h.note="Verified Segment header unavailable";return h;}
    h.segmentStart=e.data;
    if(h.segmentStart>=fileSize){h.note="Segment data outside video file";return h;}
    auto timeline=ReadVerifiedMkvTimeline(d,size);
    h.scale=timeline.timestampScaleNs;
    if(h.scale<1000||h.scale>1000000000ULL){h.note="Unusable TimestampScale";return h;}
    auto tracks=ProbeMatroskaPrefix(d,std::min<size_t>(size,1024*1024));
    for(const auto&t:tracks.tracks)if(t.type==1 && t.number){h.videoTrack=t.number;break;}
    if(!h.videoTrack){h.note="Verified video track number unavailable";return h;}
    // Cues are normally discovered from an opening SeekHead. Parse only
    // complete nested elements within the SHA-1-verified opening prefix.
    p=h.segmentStart;
    while(p<size){El child;if(!elem(d,size,p,child))break;
        if(child.id!=SEEKHEAD)continue;
        size_t a=child.data;
        while(a<child.end){El entry;if(!elem(d,child.end,a,entry))break;
            if(entry.id!=SEEK)continue;
            size_t k=entry.data;bool isCues=false,hasPos=false;uint64_t pos=0;
            while(k<entry.end){El f;if(!elem(d,entry.end,k,f))break;
                if(f.id==SEEKID){uint64_t val=0;
                    isCues=num(d,f,val)&&val==CUES;
                }else if(f.id==SEEKPOSITION)hasPos=num(d,f,pos);
            }
            if(isCues&&hasPos && sum(h.segmentStart,pos,h.cueByte) && h.cueByte<fileSize){
                h.cueFound=true;break;
            }
        }
        if(h.cueFound)break;
    }
    h.ok=true;return h;
}
// Verify the on-disk element length at Cues offset before reading its payload.
bool cuesData(uint64_t offset,uint64_t fileSize,const VerifiedMkvRead& read,
              std::vector<uint8_t>&cues,std::string&why){
    if(offset>=fileSize||fileSize-offset<16){why="Cue table offset outside selected file";return false;}
    std::vector<uint8_t> prefix;
    if(!read(offset,16,prefix)||prefix.size()!=16){why="Cue table header not in verified cache";return false;}
    size_t p=0;uint64_t id=0,count=0;bool un=false;
    if(!vint(prefix.data(),prefix.size(),p,4,false,id,un)||id!=CUES||
       !vint(prefix.data(),prefix.size(),p,8,true,count,un)||un){
        why="SeekHead points to an invalid/incomplete Cues element";return false;
    }
    if(count>kMaxCueBytes || count>fileSize-offset-p){why="Cues size exceeds bounded verified scan";return false;}
    const uint64_t needed=count+p;
    cues.reserve(size_t(needed));
    for(uint64_t at=0;at<needed;){
        const uint32_t length=uint32_t(std::min<uint64_t>(256*1024,needed-at));
        std::vector<uint8_t> b;
        if(!read(offset+at,length,b)||b.size()!=length){
            why="Cue table contains a missing SHA-1-verified piece";return false;
        }
        cues.insert(cues.end(),b.begin(),b.end());at+=length;
    }
    return true;
}
void fromCues(MkvIndexedSeek&r,const Header&h,const std::vector<uint8_t>&cueData,
              uint64_t fileSize,const VerifiedMkvRead&read){
    size_t p=0;El table;
    if(!elem(cueData.data(),cueData.size(),p,table)||table.id!=CUES){
        r.status="C.17.1: malformed verified Cue table";return;
    }
    bool have=false;uint64_t chosenTime=0,chosenByte=0, firstTime=0,firstByte=0;
    bool hasFirst=false;
    p=table.data;
    while(p<table.end && r.cueCount<100000){El point;
        if(!elem(cueData.data(),table.end,p,point))break;
        if(point.id!=POINT)continue;
        size_t x=point.data;uint64_t timeTicks=0;bool hasTime=false;
        uint64_t clusterRel=0;bool hasCluster=false;
        while(x<point.end){El e;if(!elem(cueData.data(),point.end,x,e))break;
            if(e.id==TIME)hasTime=num(cueData.data(),e,timeTicks);
            if(e.id!=POSITIONS)continue;
            size_t k=e.data;uint64_t track=0,offset=0;bool gotTrack=false,gotOffset=false;
            while(k<e.end){El f;if(!elem(cueData.data(),e.end,k,f))break;
                if(f.id==TRACK)gotTrack=num(cueData.data(),f,track);
                if(f.id==CLUSTERPOS)gotOffset=num(cueData.data(),f,offset);
            }
            if(gotTrack&&track==h.videoTrack&&gotOffset){clusterRel=offset;hasCluster=true;}
        }
        uint64_t time=0,abs=0;
        if(!hasTime||!hasCluster||!ms(timeTicks,h.scale,time)||
           !sum(h.segmentStart,clusterRel,abs)||abs>=fileSize)continue;
        ++r.cueCount;
        if(!hasFirst||time<firstTime){firstTime=time;firstByte=abs;hasFirst=true;}
        if(time<=r.requestedMs && (!have||time>chosenTime)){
            chosenTime=time;chosenByte=abs;have=true;
        }
    }
    if(!have&&hasFirst){have=true;chosenTime=firstTime;chosenByte=firstByte;}
    if(!have){r.status="C.17.1: no usable video Cue entry for this track";return;}
    r.indexed=true;r.cueBased=true;r.indexedMs=chosenTime;r.clusterByte=chosenByte;
    r.bufferPercent=percentFor(chosenByte,fileSize);
    std::vector<uint8_t> cluster;
    r.clusterHeaderCached=read(chosenByte,4,cluster)&&cluster.size()==4&&
        cluster[0]==0x1f&&cluster[1]==0x43&&cluster[2]==0xb6&&cluster[3]==0x75;
    const uint64_t offsetMs=(r.requestedMs>=chosenTime?r.requestedMs-chosenTime:chosenTime-r.requestedMs);
    r.status="C.17.1: verified video Cues "+std::to_string(r.cueCount)+
        " · requested "+std::to_string(r.requestedMs/1000)+"s · indexed Cue "+
        std::to_string(chosenTime/1000)+"s (delta "+std::to_string(offsetMs/1000)+
        "s) at selected-file byte "+std::to_string(chosenByte)+
        " · Cue table VERIFIED · cluster "+
        (r.clusterHeaderCached?"header VERIFIED":"bytes NOT cached")+
        ". A Cue is an index entry, not a verified decodable keyframe or exact seek.";
}
// Local fallback only: scan at most 4 MiB of an ALREADY VERIFIED region. Never
// claim this is the globally closest frame or a complete Cue index.
void fallback(MkvIndexedSeek&r,const Header&h,uint64_t size,uint32_t percent,
              const VerifiedMkvRead&read){
    if(percent>99)return;
    const uint64_t base=(size/100)*percent+((size%100)*percent)/100;
    const uint32_t n=uint32_t(std::min<uint64_t>(4*1024*1024,size-base));
    if(n<128)return;
    std::vector<uint8_t> bytes;bytes.reserve(n);
    for(uint32_t at=0;at<n;){
        const uint32_t block=std::min<uint32_t>(256*1024,n-at);
        std::vector<uint8_t> chunk;
        if(!read(base+at,block,chunk)||chunk.size()!=block)break;
        bytes.insert(bytes.end(),chunk.begin(),chunk.end());at+=block;
    }
    uint64_t bestDiff=std::numeric_limits<uint64_t>::max();bool found=false;
    // A cluster header may start partway through the selected byte window.
    for(size_t i=0;i+28<bytes.size();++i){
        if(bytes[i]!=0x1f||bytes[i+1]!=0x43||bytes[i+2]!=0xb6||bytes[i+3]!=0x75)continue;
        size_t p=i;
        // Parse the cluster size without requiring all its payload to be
        // present; the cluster timestamp child must itself be fully verified.
        uint64_t id=0,length=0;bool un=false;
        if(!vint(bytes.data(),bytes.size(),p,4,false,id,un)||id!=CLUSTER||
           !vint(bytes.data(),bytes.size(),p,8,true,length,un))continue;
        if(length<4||length>64*1024*1024)continue;
        const size_t end=std::min<size_t>(bytes.size(),p+size_t(std::min<uint64_t>(length,8192)));
        uint64_t ticks=0;bool hasTime=false;
        while(p<end){El child;if(!elem(bytes.data(),end,p,child))break;
            if(child.id==CLUSTERTIME){hasTime=num(bytes.data(),child,ticks);break;}
        }
        uint64_t moment=0;
        if(!hasTime||!ms(ticks,h.scale,moment))continue;
        uint64_t delta=(moment>r.requestedMs?moment-r.requestedMs:r.requestedMs-moment);
        if(!found||delta<bestDiff){r.clusterByte=base+i;r.indexedMs=moment;bestDiff=delta;found=true;}
    }
    if(found){
        r.indexed=true;r.cueBased=false;r.clusterHeaderCached=true;
        r.bufferPercent=percentFor(r.clusterByte,size);
        r.status="C.17.1: LOCAL SHA-1-verified Cluster at "+std::to_string(r.clusterByte)+
          " · time "+std::to_string(r.indexedMs/1000)+"s (difference "+
          std::to_string(bestDiff/1000)+"s). No global Cue index available; "
          "this is only the nearest header inside the cached scan window, NOT exact seeking.";
    }
}
}
MkvIndexedSeek ResolveVerifiedMkvSeek(const uint8_t*d,size_t n,uint64_t fileSize,
     uint64_t requestedMs,uint32_t localPercent,const VerifiedMkvRead&read){
    MkvIndexedSeek r;r.requestedMs=requestedMs;
    const auto h=header(d,n,fileSize);
    if(!h.ok){r.status="C.17.1: "+h.note;return r;}
    r.timestampScaleNs=h.scale;r.cueLocationKnown=h.cueFound;r.cueTableByte=h.cueByte;
    if(h.cueFound){
        r.cueBufferPercent=percentFor(h.cueByte,fileSize);
        r.bufferPercent=r.cueBufferPercent;
        std::vector<uint8_t> data;std::string why;
        if(cuesData(h.cueByte,fileSize,read,data,why)){
            r.cueTableCached=true;fromCues(r,h,data,fileSize,read);
            if(r.indexed)return r;
        }else{
            r.status="C.17.1: SeekHead locates Cues at byte "+std::to_string(h.cueByte)+
               " (file "+std::to_string(r.cueBufferPercent)+"%). "+why+
               ". Buffer the Cue-table percent first; no unverified index used.";
        }
    }else r.status="C.17.1: verified opening SeekHead has no Cues pointer. "
                       "Will check current cached byte window for a local Cluster only.";
    MkvIndexedSeek local=r;
    fallback(local,h,fileSize,localPercent,read);
    if(local.indexed){
        // Preserve Cue-table buffering advice in the status when both exist.
        if(r.cueLocationKnown&&!r.cueTableCached)local.status += " Cue table itself still needs buffering.";
        return local;
    }
    return r;
}
}
