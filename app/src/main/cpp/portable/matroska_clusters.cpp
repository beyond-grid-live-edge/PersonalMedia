#include "matroska_clusters.h"
#include <algorithm>
namespace pm_torrent {
namespace {
bool Vint(const uint8_t* d,size_t length,size_t& p,int max,bool isSize,uint64_t& result,bool& unknown) {
    if(p>=length || d[p]==0)return false;
    unsigned n=1;unsigned mask=128;
    while(n<=unsigned(max) && (d[p]&mask)==0){++n;mask>>=1;}
    if(n>unsigned(max)||n>length-p)return false;
    result=isSize?uint64_t(d[p]&(mask-1)):uint64_t(d[p]);
    for(unsigned j=1;j<n;++j)result=(result<<8)|d[p+j];
    p+=n;
    unknown=isSize && result==((uint64_t(1)<<(n*7))-1);
    return true;
}
}
ClusterMarker FindVerifiedMkvCluster(const uint8_t* d,size_t n) {
    ClusterMarker out;
    if(!d||n<16||n>1024u*1024u)return out;
    for(size_t at=0;at+12<n;++at) {
        if(d[at]!=0x1f||d[at+1]!=0x43||d[at+2]!=0xb6||d[at+3]!=0x75)continue;
        size_t p=at+4; uint64_t declared=0;bool unknown=false;
        if(!Vint(d,n,p,8,true,declared,unknown)||(!unknown && declared<2))continue;
        // Verify a complete Timecode child close to the start of the cluster.
        // Other legal EBML children (e.g. CRC-32) can precede Timecode.
        const size_t payloadBegin=p;
        const size_t window=std::min<size_t>(1024,n-payloadBegin);
        const size_t limited=(!unknown && declared<uint64_t(window))?size_t(declared):window;
        const size_t limit=payloadBegin+limited;
        size_t atChild=p;
        while(atChild+3<=limit) {
            size_t header=atChild; uint64_t id=0, size=0;bool u=false;
            if(!Vint(d,limit,header,4,false,id,u)||
               !Vint(d,limit,header,8,true,size,u)||u||size>uint64_t(n-header))break;
            if(id==0xE7 && size>=1 && size<=8) {
                uint64_t ticks=0;
                for(uint64_t j=0;j<size;++j)ticks=(ticks<<8)|d[header+size_t(j)];
                out.found=true;out.offset=at;out.timecodeTicks=ticks;return out;
            }
            if(size>uint64_t(limit-header))break;
            atChild=header+size_t(size);
        }
    }
    return out;
}
}
