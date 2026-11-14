#include "matroska_timeline.h"
#include <cmath>
#include <cstring>
#include <limits>

namespace pm_torrent {
namespace {
constexpr uint64_t kEbml=0x1a45dfa3, kSegment=0x18538067,
                   kInfo=0x1549a966, kScale=0x2ad7b1,kDuration=0x4489;
struct El { uint64_t id=0;size_t begin=0,end=0;bool unknown=false; };
// Reject truncated nested elements and all unexpected/oversized vint lengths.
bool vint(const uint8_t* data,size_t end,size_t &at,bool sizeKind,
          uint64_t& result,bool& unknown){
    if(at>=end || data[at]==0)return false;
    unsigned marker=0x80, width=1;
    while(width<=8 && !(unsigned(data[at])&marker)){
        marker>>=1;++width;
    }
    if(width>(sizeKind?8u:4u)||width>end-at)return false;
    result=sizeKind ? unsigned(data[at])&(marker-1) : unsigned(data[at]);
    for(unsigned j=1;j<width;++j)result=(result<<8)|data[at+j];
    unknown=sizeKind && result==((uint64_t(1)<<(7*width))-1);
    at+=width;
    return true;
}
bool next(const uint8_t* data,size_t end,size_t &at,El& element){
    if(at>=end)return false;
    uint64_t size=0;bool idUnknown=false;
    if(!vint(data,end,at,false,element.id,idUnknown)||
       !vint(data,end,at,true,size,element.unknown))return false;
    element.begin=at;
    if(element.unknown){
        if(element.id!=kSegment)return false;
        element.end=end;
    } else if(size>end-at){
        if(element.id!=kSegment)return false;
        element.end=end; // a partial Segment is expected in an opening prefix
    } else element.end=at+size_t(size);
    at=element.end;
    return true;
}
bool unsignedValue(const uint8_t*d,const El&e,uint64_t& out){
    if(e.unknown||e.end<=e.begin||e.end-e.begin>8)return false;
    out=0;for(size_t i=e.begin;i<e.end;++i)out=(out<<8)|d[i];
    return true;
}
bool floatValue(const uint8_t*d,const El&e,double &out){
    if(e.unknown)return false;
    const size_t n=e.end-e.begin;
    if(n!=4&&n!=8)return false;
    uint64_t bits=0;
    for(size_t i=e.begin;i<e.end;++i)bits=(bits<<8)|d[i];
    if(n==4){
        uint32_t raw=uint32_t(bits);float f=0;
        std::memcpy(&f,&raw,sizeof(f));out=double(f);
    }else std::memcpy(&out,&bits,sizeof(out));
    return std::isfinite(out)&&out>0;
}
}
MkvTimelineInfo ReadVerifiedMkvTimeline(const uint8_t*d,size_t len){
    MkvTimelineInfo result;
    result.status="C.17: need SHA-1-verified opening MKV bytes";
    if(!d||len<32||len>2*1024*1024)return result;
    size_t pos=0;El ebml;
    if(!next(d,len,pos,ebml)||ebml.id!=kEbml||ebml.unknown){
        result.status="C.17: verified prefix has no complete EBML header";return result;
    }
    El segment;bool found=false;
    while(pos<len){
        if(!next(d,len,pos,segment))break;
        if(segment.id==kSegment){found=true;break;}
    }
    if(!found){result.status="C.17: no complete Segment header in verified opening data";return result;}
    size_t child=segment.begin;El info;
    found=false;
    while(child<segment.end){
        if(!next(d,segment.end,child,info))break;
        if(info.id==kInfo && !info.unknown){found=true;break;}
    }
    if(!found){result.status="C.17: MKV Segment Info is not in the available verified prefix";return result;}
    uint64_t scale=1000000;
    double durationTicks=0;
    bool hasDuration=false;
    child=info.begin;
    while(child<info.end){
        El element;if(!next(d,info.end,child,element))break;
        if(element.id==kScale){
            if(!unsignedValue(d,element,scale)||scale<1000||scale>1000000000ULL){
                result.status="C.17: unsupported TimestampScale";return result;
            }
        }else if(element.id==kDuration){
            hasDuration=floatValue(d,element,durationTicks);
        }
    }
    result.timestampScaleNs=scale;
    if(!hasDuration){
        result.status="C.17: MKV Duration is absent or invalid; cannot label a movie-time slider yet";
        return result;
    }
    const long double ms=static_cast<long double>(durationTicks)*
                           static_cast<long double>(scale)/1000000.0L;
    if(!std::isfinite(static_cast<double>(ms))||ms<1000 || ms>86400000){
        result.status="C.17: implausible MKV duration; refusing inaccurate timeline";
        return result;
    }
    result.ready=true;
    result.durationMs=uint32_t(std::llround(ms));
    result.status="C.17: verified Segment Info · duration "+std::to_string(result.durationMs)+
        " ms · TimestampScale "+std::to_string(scale)+
        " ns/tick. Use C.17.1 to inspect the verified Cue/Cluster index; byte-only estimates may drift.";
    return result;
}
}
