#include "matroska_probe.h"
#include <algorithm>
#include <limits>
#include <sstream>

namespace pm_torrent {
namespace {
constexpr uint64_t EBML = 0x1A45DFA3, SEGMENT = 0x18538067;
constexpr uint64_t TRACKS = 0x1654AE6B, TRACK = 0xAE, CLUSTER = 0x1F43B675;
constexpr uint64_t SIMPLE_BLOCK = 0xA3, BLOCK_GROUP = 0xA0, BLOCK = 0xA1;
struct Element { uint64_t id=0; size_t begin=0, end=0; bool unknown=false; };
// Decode standard EBML VINT IDs (marker retained) and sizes (marker removed).
// Untrusted sizes must never lead to pointer arithmetic beyond the prefix.
bool Vint(const uint8_t* d,size_t limit,size_t& pos,int maxBytes,bool sizeKind,
          uint64_t& value,int* byteCount=nullptr,bool* unknown=nullptr) {
    if(pos>=limit || d[pos]==0)return false;
    uint8_t b=d[pos];int n=1;uint8_t mask=0x80;
    while(!(b&mask) && n<=maxBytes) {++n;mask>>=1;}
    if(n>maxBytes || n>int(limit-pos))return false;
    value=sizeKind ? uint64_t(b & (mask-1)) : uint64_t(b);
    for(int i=1;i<n;i++)value=(value<<8)|d[pos+i];
    pos+=size_t(n);
    if(byteCount)*byteCount=n;
    if(unknown && sizeKind) {
        *unknown = value == ((uint64_t(1)<<(7*n))-1);
    }
    return true;
}
bool Next(const uint8_t* d,size_t limit,size_t& pos,Element& el) {
    if(pos>=limit)return false;
    uint64_t id=0,len=0;bool unknown=false;
    if(!Vint(d,limit,pos,4,false,id) ||
       !Vint(d,limit,pos,8,true,len,nullptr,&unknown))return false;
    el.id=id;el.begin=pos;el.unknown=unknown;
    if(unknown)el.end=limit;
    else {
        if(len>uint64_t(limit-pos)) {
            // Segment and Cluster commonly declare sizes extending beyond the
            // verified probe prefix. Never accept a truncated nested element.
            if(id!=SEGMENT && id!=CLUSTER)return false;
            el.end=limit;
        } else el.end=pos+size_t(len);
    }
    pos=el.end;
    return true;
}
uint64_t UInt(const uint8_t* d,const Element& el) {
    if(el.end<=el.begin || el.end-el.begin>8)return 0;
    uint64_t result=0;
    for(size_t i=el.begin;i<el.end;i++)result=(result<<8)|d[i];
    return result;
}
std::string Ascii(const uint8_t* d,const Element& el,size_t max=90) {
    if(el.end<el.begin || el.end-el.begin>max)return {};
    std::string s;
    for(size_t i=el.begin;i<el.end;i++) {
        unsigned char ch=d[i];
        if(ch<0x20 || ch>0x7e)return {};
        s.push_back(char(ch));
    }
    return s;
}
void ParseDimensions(const uint8_t* d,const Element& root,MatroskaTrack& t) {
    size_t p=root.begin;
    while(p<root.end){Element el;
        if(!Next(d,root.end,p,el) || el.unknown)return;
        if(el.id==0xB0)t.width=UInt(d,el);
        if(el.id==0xBA)t.height=UInt(d,el);
    }
}
void ParseAudio(const uint8_t* d,const Element& root,MatroskaTrack& t) {
    size_t p=root.begin;
    while(p<root.end){Element el;
        if(!Next(d,root.end,p,el) || el.unknown)return;
        if(el.id==0x9F)t.channels=UInt(d,el);
    }
}
MatroskaTrack ParseTrack(const uint8_t* d,const Element& entry) {
    MatroskaTrack t;size_t p=entry.begin;
    while(p<entry.end){Element el;
        if(!Next(d,entry.end,p,el) || el.unknown)break;
        if(el.id==0xD7)t.number=UInt(d,el);
        else if(el.id==0x83)t.type=UInt(d,el);
        else if(el.id==0x86)t.codec=Ascii(d,el);
        else if(el.id==0x63A2)t.codecPrivate=el.end>el.begin;
        else if(el.id==0xE0)ParseDimensions(d,el,t);
        else if(el.id==0xE1)ParseAudio(d,el,t);
        else if(el.id==0x6D80)t.encrypted=true; // ContentEncodings requires separate handling
    }
    return t;
}
void ParseTracks(const uint8_t* d,const Element& root,MatroskaProbe& result) {
    size_t p=root.begin;
    while(p<root.end && result.tracks.size()<16){Element el;
        if(!Next(d,root.end,p,el) || el.unknown)break;
        if(el.id!=TRACK)continue;
        MatroskaTrack track=ParseTrack(d,el);
        if(track.number>0 && track.number<=127 && track.codec.size()>0)
            result.tracks.push_back(track);
    }
    result.tracksFound=!result.tracks.empty();
}
void ParseBlock(const uint8_t* d,const Element& block,MatroskaProbe& result,
                bool inGroup) {
    if(result.firstVideoPacket.found || block.end-block.begin<5)return;
    size_t p=block.begin;uint64_t track=0;
    if(!Vint(d,block.end,p,8,true,track) || p+3>block.end)return;
    bool video=false;
    for(const auto& t:result.tracks)if(t.number==track && t.type==1)video=true;
    if(!video)return;
    const int16_t timecode=static_cast<int16_t>((uint16_t(d[p])<<8)|d[p+1]);
    const uint8_t flags=d[p+2];p+=3;
    // Xiph / fixed / EBML laced blocks need a separate lacing decoder.
    if((flags&0x06)!=0 || p>=block.end || block.end-p>UINT32_MAX)return;
    result.firstVideoPacket={true, !inGroup && (flags&0x80)!=0, track,
                             timecode, uint64_t(p), uint32_t(block.end-p)};
}
void ParseGroup(const uint8_t* d,const Element& group,MatroskaProbe& result) {
    size_t p=group.begin;
    while(p<group.end){Element el;
        if(!Next(d,group.end,p,el) || el.unknown)break;
        if(el.id==BLOCK)ParseBlock(d,el,result,true);
    }
}
void ParseCluster(const uint8_t* d,const Element& cluster,MatroskaProbe& result) {
    size_t p=cluster.begin;
    while(p<cluster.end && !result.firstVideoPacket.found){Element el;
        if(!Next(d,cluster.end,p,el) || el.unknown)break;
        if(el.id==SIMPLE_BLOCK)ParseBlock(d,el,result,false);
        else if(el.id==BLOCK_GROUP)ParseGroup(d,el,result);
    }
}
}
MatroskaProbe ProbeMatroskaPrefix(const uint8_t* d,size_t len) {
    MatroskaProbe r;
    if(!d || len<8 || len>1024u*1024u) {r.issue="Need up to 1 MiB of verified opening bytes";return r;}
    size_t pos=0;Element header;
    if(!Next(d,len,pos,header)||header.id!=EBML||header.unknown){
        r.issue="Not a complete EBML header";return r;
    }
    r.isMatroska=true;
    bool segmentFound=false;
    while(pos<len){
        size_t old=pos;Element el;
        if(!Next(d,len,pos,el))break;
        if(el.id==SEGMENT) {
            segmentFound=true;
            size_t child=el.begin;
            while(child<el.end){
                size_t before=child;Element part;
                if(!Next(d,el.end,child,part))break;
                if(part.id==TRACKS)ParseTracks(d,part,r);
                else if(part.id==CLUSTER && r.tracksFound)ParseCluster(d,part,r);
                // The first cluster can fill the rest of our 1 MiB prefix.
                // We do not attempt to parse unverified missing bytes.
                if(child<=before)break;
            }
            break;
        }
        if(old==pos)break;
    }
    if(!segmentFound)r.issue="Segment not present in cached prefix";
    else if(!r.tracksFound)r.issue="Track headers not present in first verified MiB";
    else if(!r.firstVideoPacket.found)r.issue="Tracks found; first unlaced video block not in cached prefix";
    return r;
}
std::string DescribeMatroska(const MatroskaProbe& p) {
    if(!p.isMatroska)return p.issue;
    std::ostringstream o;
    o<<"MKV native demux probe · ";
    if(!p.tracksFound){o<<p.issue;return o.str();}
    for(const auto& t:p.tracks){
        o<<"["<<(t.type==1?"Video":t.type==2?"Audio":t.type==17?"Subtitle":"Other")
         <<" #"<<t.number<<" "<<t.codec;
        if(t.width>0 && t.height>0)o<<" "<<t.width<<"x"<<t.height;
        if(t.channels>0)o<<" "<<t.channels<<"ch";
        if(t.codecPrivate)o<<" config=yes";
        if(t.encrypted)o<<" content-encoding=present";
        o<<"] ";
    }
    if(p.firstVideoPacket.found){const auto& b=p.firstVideoPacket;
        o<<"· verified compressed video block #"<<b.track<<" "<<b.size
         <<" bytes at file offset "<<b.offset<<" timecode "<<b.relativeTimecode
         <<" keyframe-flag="<<(b.keyframe?"yes":"no");
    }else o<<"· "<<p.issue;
    o<<" · Frames not decoded/remuxed; AVPlayer not started";
    return o.str();
}
}
