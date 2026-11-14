#include "matroska_seek.h"
#include "matroska_probe.h"
#include <algorithm>
#include <limits>

namespace pm_torrent {
namespace {
constexpr uint64_t SEGMENT=0x18538067, INFO=0x1549A966, TRACKS=0x1654AE6B;
constexpr uint64_t CLUSTER=0x1f43b675;
struct El {uint64_t id=0;size_t head=0,begin=0,end=0;bool unknown=false;};
bool vint(const uint8_t* d,size_t length,size_t &p,unsigned max,bool sizeValue,
          uint64_t &value,bool &unknown){
    if(p>=length || d[p]==0)return false;
    unsigned n=1;unsigned mask=0x80;
    while(n<=max && !(d[p]&mask)){++n;mask>>=1;}
    if(n>max||n>length-p)return false;
    value=sizeValue?(d[p]&(mask-1)):d[p];
    for(unsigned i=1;i<n;++i)value=(value<<8)|d[p+i];
    p+=n;unknown=sizeValue&&value==((uint64_t(1)<<(7*n))-1);
    return true;
}
bool el(const uint8_t* d,size_t limit,size_t& p,El& e,bool allowPartial=false){
    e.head=p;
    uint64_t n=0;bool u=false;
    if(!vint(d,limit,p,4,false,e.id,u)||!vint(d,limit,p,8,true,n,u))return false;
    e.begin=p;e.unknown=u;
    if(u||n>limit-p){
        if(!allowPartial || (e.id!=SEGMENT && e.id!=CLUSTER))return false;
        e.end=limit;
    } else e.end=p+size_t(n);
    p=e.end;
    return true;
}
uint64_t number(const uint8_t*d,const El&e){
    if(e.end<=e.begin||e.end-e.begin>8)return 0;
    uint64_t v=0;for(size_t p=e.begin;p<e.end;++p)v=(v<<8)|d[p];return v;
}
struct Header {
    std::vector<uint8_t> data;
    uint64_t scale=1000000;
    uint64_t video=0,audio=0;
    std::string error;
};
Header getHeader(const uint8_t*d,size_t n){
    Header h;
    if(!d||n<64||n>2*1024*1024){h.error="Need 64 bytes–2 MiB of verified MKV opening metadata";return h;}
    size_t p=0;El ebml;
    if(!el(d,n,p,ebml)||ebml.id!=0x1a45dfa3){h.error="Verified prefix lacks EBML header";return h;}
    El segment;bool found=false;
    while(p<n){if(!el(d,n,p,segment,true))break;if(segment.id==SEGMENT){found=true;break;}}
    if(!found){h.error="No verified Matroska Segment header";return h;}
    const auto parsed=ProbeMatroskaPrefix(d,std::min<size_t>(n,1024*1024));
    for(const auto&t:parsed.tracks){
        if(!h.video && t.type==1 && t.codec=="V_MPEGH/ISO/HEVC" && t.codecPrivate && !t.encrypted)h.video=t.number;
        if(!h.audio && t.type==2 && t.codec=="A_AAC" && t.codecPrivate && !t.encrypted)h.audio=t.number;
    }
    if(!h.video||!h.audio){h.error="Verified MKV header lacks compatible HEVC + AAC track metadata";return h;}
    std::vector<uint8_t> info,tracks;
    p=segment.begin;
    while(p<n){El item;if(!el(d,n,p,item,true))break;
        if(item.unknown && item.id!=CLUSTER && item.id!=SEGMENT)break;
        if(item.id==INFO && !item.unknown){
            info.assign(d+item.head,d+item.end);
            size_t q=item.begin;
            while(q<item.end){El child;if(!el(d,item.end,q,child))break;
                if(child.id==0x2ad7b1)h.scale=number(d,child);
            }
        } else if(item.id==TRACKS && !item.unknown) tracks.assign(d+item.head,d+item.end);
        if(!info.empty()&&!tracks.empty())break;
    }
    if(info.empty()||tracks.empty()){
        h.error="Info/Tracks not fully available in opening metadata (buffer opening first)";return h;
    }
    if(h.scale<1000||h.scale>1000000000ULL){h.error="Unsupported TimestampScale";return h;}
    // Reconstruct a minimal EBML document. The original Segment's declared
    // byte length is not valid once clusters are relocated; use unknown size.
    h.data.assign(d,d+ebml.end);
    const uint8_t openSegment[]={0x18,0x53,0x80,0x67,0x01,0xff,0xff,0xff,0xff,0xff,0xff,0xff};
    h.data.insert(h.data.end(),openSegment,openSegment+sizeof(openSegment));
    h.data.insert(h.data.end(),info.begin(),info.end());
    h.data.insert(h.data.end(),tracks.begin(),tracks.end());
    return h;
}
struct ClusterEntry {size_t begin=0,end=0;uint64_t ticks=0;uint32_t key=0,audio=0;};
bool assessCluster(const uint8_t*d,size_t n,size_t at,uint64_t video,uint64_t audio,ClusterEntry& entry){
    size_t p=at;El cl;
    if(!el(d,n,p,cl)||cl.id!=CLUSTER||cl.unknown)return false;
    if(cl.end-cl.begin>16*1024*1024 || cl.end-cl.begin<8)return false;
    size_t c=cl.begin;bool gotTimestamp=false;
    while(c<cl.end){El e;if(!el(d,cl.end,c,e))return false;
        if(e.id==0xe7 && e.end>e.begin && e.end-e.begin<=8){entry.ticks=number(d,e);gotTimestamp=true;}
        if(e.id!=0xa3 || !gotTimestamp)continue;
        size_t k=e.begin;uint64_t track=0;bool u=false;
        if(!vint(d,e.end,k,8,true,track,u)||k+3>=e.end)continue;
        const uint8_t flags=d[k+2];
        if(flags&0x06)continue; // lacing unsupported in bounded remuxer
        if(track==audio)++entry.audio;
        if(track==video && (flags&0x80))++entry.key;
    }
    if(!gotTimestamp)return false;
    entry.begin=at;entry.end=cl.end;return true;
}
}
MkvLaterWindow PrepareVerifiedMkvLaterWindow(const uint8_t*prefix,size_t prefixSize,
            const uint8_t*later,size_t laterSize,uint64_t laterFileOffset){
    MkvLaterWindow r;
    Header h=getHeader(prefix,prefixSize);
    if(!h.error.empty()){r.diagnostic=h.error;return r;}
    if(!later||laterSize<4096||laterSize>32*1024*1024){r.diagnostic="Need 4 KiB–32 MiB of verified later MKV bytes";return r;}
    r.timecodeScaleNs=h.scale;
    std::vector<ClusterEntry> clusters;
    // Scan for structurally complete Cluster elements, not byte signatures alone.
    for(size_t pos=0;pos+24<laterSize;){
        if(later[pos]!=0x1f||later[pos+1]!=0x43||later[pos+2]!=0xb6||later[pos+3]!=0x75){++pos;continue;}
        ClusterEntry found;
        if(assessCluster(later,laterSize,pos,h.video,h.audio,found)){
            clusters.push_back(found);pos=found.end;
        }else ++pos;
    }
    if(clusters.empty()){r.diagnostic="No complete, verified later MKV Clusters within scan window";return r;}
    size_t start=0;
    while(start<clusters.size() && !clusters[start].key)++start;
    if(start==clusters.size()){
        r.diagnostic="Verified later clusters found, but no HEVC SimpleBlock keyframe; cannot safely start decoding";return r;
    }
    const ClusterEntry& first=clusters[start];
    if(first.ticks>UINT64_MAX/h.scale){r.diagnostic="Cluster timestamp overflow";return r;}
    r.firstClusterTimeMs=first.ticks*h.scale/1000000ULL;
    r.firstClusterFileByte=laterFileOffset+first.begin;
    r.virtualMkv=std::move(h.data);
    // The bounded remuxer accepts up to 64 MiB. Keep full verified clusters,
    // and never append a truncated cluster or read outside the downloaded range.
    for(size_t i=start;i<clusters.size();++i){
        const auto& c=clusters[i];
        if(c.begin<first.begin || r.virtualMkv.size()>32*1024*1024-(c.end-c.begin))break;
        r.virtualMkv.insert(r.virtualMkv.end(),later+c.begin,later+c.end);
        ++r.completeClusters;r.keyframeBlocks+=c.key;r.audioBlocks+=c.audio;
    }
    if(r.keyframeBlocks==0 || r.audioBlocks==0){
        r.virtualMkv.clear();r.diagnostic="No aligned HEVC keyframe and AAC data in verified later clusters";return r;
    }
    r.ready=true;
    r.diagnostic="VERIFIED later MKV: scale "+std::to_string(r.timecodeScaleNs)+
       " ns/tick · first cluster ~"+std::to_string(r.firstClusterTimeMs)+
       " ms · clusters "+std::to_string(r.completeClusters)+
       " · HEVC key blocks "+std::to_string(r.keyframeBlocks)+
       " · AAC blocks "+std::to_string(r.audioBlocks)+
       ". Cluster time is not an exact playable start time.";
    return r;
}
}
