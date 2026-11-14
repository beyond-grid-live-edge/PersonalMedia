// PersonalMedia native August DVB-T405 / Siano WiFi TV adapter.
// Protocol derived from user-provided v1.5.5 reference and publicly documented
// Siano host messages. No Android libraries, Python or browser server required.
#include "native_live_tv.h"
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <cstring>
#include <cstdlib>
#include <algorithm>
#include <array>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <ctime>
#include <deque>
#include <limits>
#include <iterator>
#include <exception>
#include <map>
#include <mutex>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

namespace pm_live_tv {
namespace {
constexpr int kHifTask=11, kHostLib=150;
constexpr uint16_t kVersionReq=668,kVersionRes=669,kTuneReq=561,kTuneRes=562,
    kAddPidReq=601,kStatsReq=653,kSianoData=693,kSignal=827,kNoSignal=828,
    kBatteryReq=750,kBatteryRes=751;
constexpr std::size_t kMaxStreamPackets=18000; // 3.38 MiB rolling live edge
constexpr uint16_t kSiPids[]={0,0x10,0x11,0x12,0x14};
uint16_t be16(const uint8_t*p){return uint16_t(p[0]<<8)|p[1];}
uint16_t le16(const uint8_t*p){return uint16_t(p[0])|uint16_t(p[1]<<8);}
void put16(std::vector<uint8_t>&v,uint16_t x){v.push_back(uint8_t(x));v.push_back(uint8_t(x>>8));}
void put32(std::vector<uint8_t>&v,uint32_t x){for(int i=0;i<4;++i)v.push_back(uint8_t(x>>(8*i)));}
std::string quote(const std::string&s){std::string o="\"";for(unsigned char c:s){
    if(c=='\"'||c=='\\'){o+='\\';o+=char(c);}else if(c>=32&&c<127)o+=char(c);
    else if(c>=128)o+='?';else if(c=='\n')o+="\\n";else if(c=='\r')o+="\\r";
}return o+'\"';}
std::string text(const uint8_t*p,std::size_t n){std::string s; if(n&&p[0]<0x20){++p;--n;}
  for(std::size_t i=0;i<n;++i){uint8_t c=p[i];if(c>=32 &&c<127)s+=char(c);else if(c>=160)s+='?';}return s;}
int pid(const uint8_t*p){return (int(p[1]&0x1f)<<8)|p[2];}
std::string utc(int64_t sec){std::time_t t=std::time_t(sec);std::tm out{};if(!gmtime_r(&t,&out))return "";
    char buf[32];if(std::strftime(buf,sizeof(buf),"%Y-%m-%dT%H:%M:%SZ",&out)==0)return "";return buf;}
int bcd(uint8_t x){return ((x>>4)*10)+(x&15);}
int64_t dvb_time(const uint8_t*p){if(p[0]==255&&p[1]==255)return 0;
    int64_t mjd=be16(p);int h=bcd(p[2]),m=bcd(p[3]),s=bcd(p[4]);
    if(h>23||m>59||s>59)return 0;
    return (mjd-40587)*86400+h*3600+m*60+s;}
struct Event{std::string name,description;int64_t start=0,end=0;};
struct Channel{std::string key,name,provider;int sid=0,tsid=0,onid=0,frequency=0,bandwidth=8,lcn=0,pmt=0,pcr=0x1fff;
    bool encrypted=false;std::set<int> streams;Event now,next;std::map<int64_t,Event> events;};
struct Assembly {std::vector<uint8_t> data;int continuity=-1;};
struct Packet {uint64_t seq=0;std::array<uint8_t,188> data;};
struct State{
    std::mutex mu;std::condition_variable cv;std::thread reader,scan,server;
    std::atomic<bool> stopping{false},scan_stop{false};
    int recvfd=-1,sendfd=-1,listenfd=-1,clientfd=-1;
    std::string host="192.168.1.1",error,message="Connect to the August 405 Wi-Fi network to begin";
    int control_port=5002,receive_port=5001,frequency=0,bandwidth=8;
    std::atomic<int> datagrams{0},ts_packets{0},siano_messages{0};
    int last_message=0;bool replied=false,signal=false,signal_known=false;
    std::string last_disconnect_reason,last_control_error;
    uint64_t control_sends=0;
    // Battery protocol: APK SmsLiteBatteryLevel_Req sends four 32-bit words.
    // Reply field semantics differ by firmware and are not verified: never
    // report any payload word as a percentage without a validated schema.
    uint64_t battery_requests=0,battery_replies=0;
    std::vector<uint32_t> battery_raw_words;
    int battery_payload_bytes=0;
    std::string battery_raw_hex;
    std::chrono::steady_clock::time_point battery_requested{},battery_received{};
    std::atomic<bool> receive_faulted{false};
    int tsid=0,onid=0;std::map<int,int> pmts;std::map<std::string,Channel> channels;
    std::map<int,Assembly> assembly;
    // A PMT can span several 188-byte transport packets: cache complete PSI
    // sections, never just the last packet of a table.
    std::map<int,std::vector<uint8_t>> last_sections;
    std::set<int> requested_pmts;
    std::string pending_key;
    int pending_sid=0,pending_pmt=0;
    std::chrono::steady_clock::time_point last_pmt_request{},last_pat_received{},last_pmt_received{};
    uint64_t pat_sections=0,pmt_sections=0,pmt_requests=0,pmt_retries=0,stream_pid_requests=0;
    uint64_t eit_pf_sections=0,eit_schedule_sections=0,eit_programmes=0;
    std::map<int,int> lcn_by_sid;
    std::deque<Packet> live;uint64_t next_seq=0,queue_drops=0;
    std::string selected;std::set<int> selected_pids,selected_media_pids;int http_port=0;
    std::chrono::steady_clock::time_point last_selected_media{},last_recovery{};
    uint64_t selected_media_packets=0,recovery_attempts=0,recovery_successes=0;
    unsigned recovery_streak=0;bool recovery_pending=false;
    std::string recovery_action="Idle";
    bool scanning=false;int scan_progress=0,scan_total=0,scan_found=0;
    std::chrono::steady_clock::time_point last_recv{},last_siano_reply{},last_ts_recv{};
};
State& S(){static State* s=new State();return *s;}
std::vector<uint8_t> msg(uint16_t type,const std::vector<uint32_t>&params={}){
    std::vector<uint8_t> v;put16(v,type);v.push_back(kHostLib);v.push_back(kHifTask);
    put16(v,uint16_t(8+params.size()*4));put16(v,1);for(auto x:params)put32(v,x);return v;}
bool send_msg(const std::vector<uint8_t>&data){State&s=S();int fd;std::string host;int port;
    {std::lock_guard<std::mutex>g(s.mu);fd=s.sendfd;host=s.host;port=s.control_port;}
    if(fd<0)return false;
    sockaddr_in dest{};dest.sin_family=AF_INET;dest.sin_port=htons(uint16_t(port));
    if(inet_pton(AF_INET,host.c_str(),&dest.sin_addr)!=1)return false;
    ssize_t sent=sendto(fd,data.data(),data.size(),0,reinterpret_cast<sockaddr*>(&dest),sizeof(dest));
    const int send_error=sent==ssize_t(data.size())?0:errno;
    {std::lock_guard<std::mutex>g(s.mu);++s.control_sends;
      if(send_error){s.last_control_error="Control UDP send failed (errno "+std::to_string(send_error)+")";
        s.error=s.last_control_error;}
      else {s.last_control_error.clear();
        if(s.error.find("Control UDP send failed")==0)s.error.clear();}}
    return send_error==0;}
// The original ARM32 SmsLiteBatteryLevel_Req constructs an 8-byte
// Siano header + four little-endian uint32 arguments (24 bytes total).
// This is a READ query, not a battery calibration or power-mode command.
[[maybe_unused]] bool request_battery(){
    State&s=S();
    {std::lock_guard<std::mutex>g(s.mu);if(s.sendfd<0)return false;}
    const bool ok=send_msg(msg(kBatteryReq,{0,0,0,0}));
    if(ok){std::lock_guard<std::mutex>g(s.mu);
        ++s.battery_requests;s.battery_requested=std::chrono::steady_clock::now();}
    return ok;
}
void capture_battery(State&s,const uint8_t*payload,std::size_t length){
    // Caller holds mu; sender address, Siano header length and message ID
    // were already validated by receive_loop. Bound all untrusted diagnostics.
    ++s.battery_replies;s.battery_received=std::chrono::steady_clock::now();
    s.battery_payload_bytes=int(length);
    s.battery_raw_words.clear();s.battery_raw_hex.clear();
    constexpr char digits[]="0123456789ABCDEF";
    const auto max_bytes=std::min<std::size_t>(length,64);
    for(std::size_t i=0;i<max_bytes;++i){
        if(i)s.battery_raw_hex.push_back(' ');
        s.battery_raw_hex.push_back(digits[payload[i]>>4]);
        s.battery_raw_hex.push_back(digits[payload[i]&15]);
    }
    for(std::size_t i=0;i+4<=max_bytes;++i){
        const uint32_t x=uint32_t(payload[i]) |(uint32_t(payload[i+1])<<8)
            |(uint32_t(payload[i+2])<<16)|(uint32_t(payload[i+3])<<24);
        s.battery_raw_words.push_back(x);
        i+=3;
    }
}
void add_pid(int p){if(p>=0&&p<=0x1fff)send_msg(msg(kAddPidReq,{uint32_t(p)}));}
void set_error(const std::string&e){State&s=S();std::lock_guard<std::mutex>g(s.mu);s.error=e;s.message=e;}
// PSI/SI section handlers called with s.mu held; never do socket I/O here.
void section(State&s,int packet_pid,const std::vector<uint8_t>&sec){
 if(sec.size()<8)return;
 const uint8_t* p=sec.data();int tid=p[0];std::size_t end=sec.size()>=4?sec.size()-4:0;
 if(packet_pid==0x10 &&(tid==0x40||tid==0x41)&&sec.size()>=16){
    std::size_t network_len=((p[8]&15)<<8)|p[9];std::size_t pos=10+network_len;
    if(pos+2>end)return;
    std::size_t loop_len=((p[pos]&15)<<8)|p[pos+1];pos+=2;
    std::size_t stop=std::min(end,pos+loop_len);
    while(pos+6<=stop){int tsid=be16(p+pos),onid=be16(p+pos+2);
      std::size_t desc_len=((p[pos+4]&15)<<8)|p[pos+5];
      std::size_t d=pos+6,dend=std::min(stop,d+desc_len);
      while(d+2<=dend){int tag=p[d],length=p[d+1];if(d+2+length>dend)break;
        if(tag==0x83||tag==0x87){
          for(std::size_t i=d+2;i+4<=d+2+std::size_t(length);i+=4){
            int sid=be16(p+i),number=((p[i+2]&3)<<8)|p[i+3];
            if(number>0){s.lcn_by_sid[sid]=number;
              for(auto& row:s.channels)if(row.second.sid==sid &&
                  row.second.tsid==tsid&&row.second.onid==onid)row.second.lcn=number;}
          }
        }
        if(tag==0x5a&&length>=5){uint64_t freq=(uint64_t(p[d+2])<<24)|
          (uint64_t(p[d+3])<<16)|(uint64_t(p[d+4])<<8)|p[d+5];freq*=10;
          if(freq>=42000000&&freq<=870000000){for(auto& c:s.channels){
             if(c.second.tsid==tsid&&c.second.onid==onid)c.second.frequency=int(freq);}}
        }
        d+=2+length;
      }
      pos=dend;
    }
 } else if(tid==0 && packet_pid==0 && sec.size()>=12){
    s.tsid=be16(p+3);++s.pat_sections;s.last_pat_received=std::chrono::steady_clock::now();
    s.last_sections[0]=sec;
    for(std::size_t i=8;i+4<=end;i+=4){
      int sid=be16(p+i);if(!sid)continue;
      int map_pid=((p[i+2]&31)<<8)|p[i+3];
      if(map_pid==0||map_pid==0x1fff)continue;
      s.pmts[sid]=map_pid;
      for(auto& entry:s.channels)if(entry.second.sid==sid && entry.second.frequency==s.frequency){
          entry.second.pmt=map_pid;
      }
    }
 } else if(tid==2 && sec.size()>=16 && packet_pid>0 && packet_pid<0x1fff){
    // PMTs are often multi-packet and must be fully reconstructed before
    // attempting to start playback. Reject PMTs for another service.
    int service_id=be16(p+3);
    auto mapped=s.pmts.find(service_id);
    if(mapped==s.pmts.end() || mapped->second!=packet_pid)return;
    ++s.pmt_sections;s.last_pmt_received=std::chrono::steady_clock::now();
    s.last_sections[packet_pid]=sec;
    int sid=be16(p+3), program_len=((p[10]&15)<<8)|p[11];std::size_t i=12+program_len;
    int pcr=((p[8]&31)<<8)|p[9];std::set<int> tracks;
    while(i+5<=end){int stream=((p[i+1]&31)<<8)|p[i+2];int tag=p[i];
        int len=((p[i+3]&15)<<8)|p[i+4];if(i+5+len>end)break;
        if(tag==1||tag==2||tag==3||tag==4||tag==6||tag==15||tag==17||tag==27||tag==36||tag==0x81)tracks.insert(stream);
        i+=5+len;
    }
    for(auto& pair:s.channels)if(pair.second.sid==sid && pair.second.frequency==s.frequency){
        pair.second.pmt=packet_pid;pair.second.pcr=pcr;pair.second.streams=tracks;
    }
 } else if(packet_pid==0x11 && (tid==0x42||tid==0x46) && sec.size()>=15){
    int tsid=be16(p+3),onid=be16(p+8);s.onid=onid;
    for(std::size_t i=11;i+5<=end;){int sid=be16(p+i);int len=((p[i+3]&15)<<8)|p[i+4];
        std::size_t stop=std::min(end,i+5+std::size_t(len));std::string name,provider;
        for(std::size_t j=i+5;j+2<=stop;){int dl=p[j+1];if(j+2+dl>stop)break;
          if(p[j]==0x48 &&dl>=3){int pn=p[j+3];if(4+pn<=2+dl){
              provider=text(p+j+4,pn);std::size_t np=j+4+pn;if(np<j+2+dl){
              int nn=p[np];if(np+1+nn<=j+2+dl)name=text(p+np+1,nn);
          }}}
          j+=2+dl;
        }
        if(!name.empty()){
          std::string key=std::to_string(onid)+":"+std::to_string(tsid)+":"+std::to_string(sid);
          auto& c=s.channels[key];c.key=key;c.sid=sid;c.tsid=tsid;c.onid=onid;c.frequency=s.frequency;
          c.bandwidth=s.bandwidth;c.name=name;c.provider=provider;c.encrypted=(p[i+3]&0x10)!=0;
          if(s.lcn_by_sid.count(sid))c.lcn=s.lcn_by_sid[sid];
          auto found=s.pmts.find(sid);if(found!=s.pmts.end())c.pmt=found->second;
        }
        i=stop;
    }
 } else if(packet_pid==0x12 && (tid>=0x4e&&tid<=0x6f) &&sec.size()>=18){
    int sid=be16(p+3),tsid=be16(p+8),onid=be16(p+10);
    const std::string key=std::to_string(onid)+":"+std::to_string(tsid)+":"+std::to_string(sid);
    auto found=s.channels.find(key);if(found==s.channels.end())return;
    if(tid==0x4e||tid==0x4f)++s.eit_pf_sections;
    else ++s.eit_schedule_sections;
    int64_t now=std::time(nullptr);
    for(std::size_t i=14;i+12<=end;){int64_t start=dvb_time(p+i+2);
      int duration=bcd(p[i+7])*3600+bcd(p[i+8])*60+bcd(p[i+9]);
      int len=((p[i+10]&15)<<8)|p[i+11];std::size_t stop=std::min(end,i+12+std::size_t(len));
      std::string title,description;
      for(std::size_t j=i+12;j+2<=stop;){int dl=p[j+1];if(j+2+dl>stop)break;
        if(p[j]==0x4d &&dl>=5){int nl=p[j+5];if(j+6+nl<=j+2+dl){title=text(p+j+6,nl);
            std::size_t dp=j+6+nl;if(dp<j+2+dl){int dn=p[dp];if(dp+1+dn<=j+2+dl)description=text(p+dp+1,dn);}}}
        j+=2+dl;
      }
      if(!title.empty()&&start>0&&duration>0){++s.eit_programmes;
        Event e{title,description,start,start+duration};
        // Retain the short/schedule EIT in a bounded map keyed by actual UTC start.
        // The guide and Now/Next are recalculated from timestamps at read time,
        // so old programme titles never remain "Now" after the boundary passes.
        auto& events=found->second.events;
        if(e.end>=now-3600 && e.start<=now+7*86400) events[e.start]=e;
        for(auto it=events.begin();it!=events.end();){
          if(it->second.end<now-3600) it=events.erase(it);else ++it;
        }
        while(events.size()>96) events.erase(std::prev(events.end()));
        if(start<=now &&now<e.end)found->second.now=e;
        else if(start>=now &&(!found->second.next.start||start<found->second.next.start))found->second.next=e;}
      i=stop;
    }
 }
}
void parse_ts(State&s,const uint8_t*packet){if(packet[0]!=0x47)return;
  int p=pid(packet);int afc=(packet[3]>>4)&3;if(afc==0||afc==2)return;
  int pos=4;if(afc==3){pos+=1+packet[4];if(pos>=188)return;}
  bool start=(packet[1]&0x40)!=0;auto& a=s.assembly[p];
  int cc=packet[3]&15;if(a.continuity>=0&&cc!=((a.continuity+1)&15)&&!start)a.data.clear();a.continuity=cc;
  if(start){int pointer=packet[pos++];if(pos+pointer>188){a.data.clear();return;}
    // Complete the previous section using pointer bytes, if possible.
    if(pointer &&!a.data.empty())a.data.insert(a.data.end(),packet+pos,packet+pos+pointer);
    pos+=pointer;
    // Old partial section is dropped if not complete; next payload begins here.
    if(!a.data.empty() &&a.data.size()>=3){int need=3+((a.data[1]&15)<<8)+a.data[2];
      if(need>=8&&need<=4096&&a.data.size()>=std::size_t(need)){
        std::vector<uint8_t> old(a.data.begin(),a.data.begin()+need);section(s,p,old);}}
    a.data.clear();
  }
  if(pos>=188)return;
  if(a.data.size()>4096){a.data.clear();return;}
  a.data.insert(a.data.end(),packet+pos,packet+188);
  while(a.data.size()>=3){while(!a.data.empty()&&a.data[0]==0xff)a.data.erase(a.data.begin());
    if(a.data.size()<3)break;
    int need=3+((a.data[1]&15)<<8)+a.data[2];
    if(need<8||need>4096){a.data.erase(a.data.begin());continue;}
    if(a.data.size()<std::size_t(need))break;
    std::vector<uint8_t> sec(a.data.begin(),a.data.begin()+need);a.data.erase(a.data.begin(),a.data.begin()+need);
    section(s,p,sec);
  }
}
void packet(State&s,const uint8_t*data){int p=pid(data);
  const bool is_si=p==0 || p==0x11 || p==0x12 || p==0x10 || p==0x14;
  bool is_pmt=false;
  for(const auto& x:s.pmts)if(x.second==p){is_pmt=true;break;}
  // Do not feed MPEG PES/video/audio payload to the DVB section assembler.
  // On a high-bitrate multiplex that causes needless allocations and false PSI.
  if(is_si||is_pmt)parse_ts(s,data);
  if(!s.selected.empty() &&s.selected_media_pids.count(p)>0){
      s.last_selected_media=std::chrono::steady_clock::now();
      ++s.selected_media_packets;
      if(s.recovery_pending){
        s.recovery_pending=false;s.recovery_streak=0;++s.recovery_successes;
        s.recovery_action="Transport restored";
      }
  }
  if(!s.selected.empty() &&s.selected_pids.find(p)!=s.selected_pids.end()){
    Packet row;row.seq=++s.next_seq;std::memcpy(row.data.data(),data,188);s.live.push_back(row);
    if(s.live.size()>kMaxStreamPackets){s.live.pop_front();++s.queue_drops;}
    s.cv.notify_all();
  }
}
void receive_loop(){State&s=S();std::vector<uint8_t>buf(65536);
  auto next_heartbeat=std::chrono::steady_clock::now()+std::chrono::seconds(8);
  while(!s.stopping.load()){
    const auto now=std::chrono::steady_clock::now();
    if(now>=next_heartbeat){send_msg(msg(kStatsReq,{0}));next_heartbeat=now+std::chrono::seconds(8);}
    int fd;std::string host;{std::lock_guard<std::mutex>g(s.mu);fd=s.recvfd;host=s.host;}
    if(fd<0)break;
    sockaddr_in addr{};socklen_t slen=sizeof(addr);
    ssize_t n=recvfrom(fd,buf.data(),buf.size(),0,reinterpret_cast<sockaddr*>(&addr),&slen);
    if(n<0){if(errno==EAGAIN||errno==EWOULDBLOCK||errno==EINTR)continue;
      if(!s.stopping.load()){s.receive_faulted.store(true);set_error("T405 UDP receive failed (errno "+std::to_string(errno)+")");}break;}
    if(n==0)continue;
    in_addr source{};if(inet_pton(AF_INET,host.c_str(),&source)!=1||source.s_addr!=addr.sin_addr.s_addr)continue;
    int type=0;std::size_t off=0,length=std::size_t(n);
    if(n>=8){type=le16(buf.data());int mlen=le16(buf.data()+4);
      if(type>=500&&type<=900&&mlen>=8&&mlen<=n){off=8;length=std::size_t(mlen);}}
    {std::lock_guard<std::mutex>g(s.mu);++s.datagrams;s.last_recv=std::chrono::steady_clock::now();
    if(off==8){s.last_message=type;++s.siano_messages;
       s.replied=true;s.last_siano_reply=std::chrono::steady_clock::now();
       if(type==kVersionRes){s.replied=true;s.message="August 405 replied to Siano extended-version probe";}
       if(type==kBatteryRes){
         capture_battery(s,buf.data()+8,length-8);
         s.replied=true; // A genuine response from the configured tuner address.
       }
       if(type==kTuneRes)s.message="T405 tune acknowledged; collecting DVB tables";
       if(type==kSignal){s.signal=true;s.signal_known=true;}
       if(type==kNoSignal){s.signal=false;s.signal_known=true;}
    }
    const uint8_t*p=buf.data()+off;std::size_t len=length-off;
    // Accept raw TS or Siano encapsulated TS; skip arbitrary control bytes.
    for(std::size_t i=0;i+188<=len;){
       if(p[i]!=0x47){++i;continue;}
       if(i+376<=len&&p[i+188]!=0x47){++i;continue;}
       packet(s,p+i);++s.ts_packets;s.last_ts_recv=std::chrono::steady_clock::now();i+=188;
    }
    }
    // A DVB multiplex can advertise dozens of PMT PIDs. Programming every
    // filter can exhaust T405 firmware filter slots and block the selected
    // programme. Only the requested service's PMT is installed in Play().
  }
}
void reset_frequency(State&s,int freq,int bw){s.frequency=freq;s.bandwidth=bw;s.pmts.clear();s.requested_pmts.clear();s.lcn_by_sid.clear();s.assembly.clear();s.last_sections.clear();s.pending_key.clear();s.pending_sid=0;s.pending_pmt=0;s.last_pmt_request={};s.last_pat_received={};s.last_pmt_received={};s.pat_sections=0;s.pmt_sections=0;s.pmt_requests=0;s.pmt_retries=0;s.selected.clear();s.selected_pids.clear();s.selected_media_pids.clear();s.last_selected_media={};
    s.selected_media_packets=0;s.recovery_streak=0;s.recovery_pending=false;
    s.live.clear();s.next_seq++;s.signal_known=false;
    for(auto& row:s.channels)if(row.second.frequency==freq){row.second.pmt=0;row.second.pcr=0x1fff;row.second.streams.clear();}
    s.cv.notify_all();}
void scan_loop(std::string profile){
  State&s=S();int last=profile=="eu_uhf"?69:48;
  int first_active=0,multiplexes=0;
  for(int ch=21;ch<=last &&!s.scan_stop.load()&&!s.stopping.load();++ch){
    const int freq=(474+(ch-21)*8)*1000000;
    const int before=s.ts_packets.load();
    Tune(freq,8);
    // DVB SDT/EIT broadcast periodically. Stay longer on live multiplexes.
    for(int i=0;i<11&&!s.scan_stop.load()&&!s.stopping.load();++i)
      std::this_thread::sleep_for(std::chrono::milliseconds(110));
    const bool active=s.ts_packets.load()>before;
    if(active){
      if(!first_active)first_active=freq;
      ++multiplexes;
      for(int i=0;i<17&&!s.scan_stop.load()&&!s.stopping.load();++i)
        std::this_thread::sleep_for(std::chrono::milliseconds(110));
    }
    {std::lock_guard<std::mutex>g(s.mu);s.scan_progress=ch-20;
      s.scan_found=multiplexes;
      s.message="Scanning UHF "+std::to_string(ch)+" · "+std::to_string(freq/1000000)+" MHz";}
  }
  const bool cancelled=s.scan_stop.load()||s.stopping.load();
  if(!cancelled && first_active){
    // Restore the first usable multiplex for the live guide.
    Tune(first_active,8);
    std::this_thread::sleep_for(std::chrono::milliseconds(700));
  }
  {std::lock_guard<std::mutex>g(s.mu);s.scanning=false;
    s.message=cancelled?"Channel scan cancelled":
      "Scan complete · "+std::to_string(multiplexes)+" active multiplexes · "+
        std::to_string(s.channels.size())+" services";}
}
bool send_all(int fd,const uint8_t*p,std::size_t n){while(n){ssize_t written=send(fd,p,n,MSG_NOSIGNAL);
 if(written<0&&errno==EINTR)continue;
 if(written<=0)return false;
 p+=written;n-=std::size_t(written);}return true;}
// Build DVB/MPEG-2 CRC for a service-specific PAT, so Media3 does not see
// unrelated programmes whose PMT filters were intentionally not installed.
uint32_t mpeg_crc(const uint8_t* bytes,std::size_t n){
  uint32_t crc=0xffffffffu;
  for(std::size_t i=0;i<n;++i){crc^=uint32_t(bytes[i])<<24;
    for(int bit=0;bit<8;++bit)crc=(crc&0x80000000u)?(crc<<1)^0x04c11db7u:crc<<1;}
  return crc;
}
std::vector<uint8_t> selected_pat(int tsid,int sid,int pmt){
  std::vector<uint8_t> sec={0x00,0xb0,0x0d,uint8_t(tsid>>8),uint8_t(tsid),0xc1,0,0,
    uint8_t(sid>>8),uint8_t(sid),uint8_t(0xe0|((pmt>>8)&0x1f)),uint8_t(pmt)};
  uint32_t crc=mpeg_crc(sec.data(),sec.size());
  for(int shift=24;shift>=0;shift-=8)sec.push_back(uint8_t(crc>>shift));
  return sec;
}
std::vector<std::array<uint8_t,188>> psi_packets(int section_pid,const std::vector<uint8_t>&sec){
  std::vector<std::array<uint8_t,188>> result;
  if(sec.size()<8||sec.size()>4096||section_pid<0||section_pid>0x1fff)return result;
  std::size_t offset=0;int continuity=0;
  while(offset<sec.size()){
    std::array<uint8_t,188> row;row.fill(0xff);
    row[0]=0x47;row[1]=uint8_t((section_pid>>8)&0x1f);
    if(offset==0)row[1]|=0x40;
    row[2]=uint8_t(section_pid);row[3]=uint8_t(0x10|(continuity++&0xf));
    std::size_t pos=4;
    if(offset==0)row[pos++]=0; // pointer field
    const auto n=std::min<std::size_t>(188-pos,sec.size()-offset);
    std::memcpy(row.data()+pos,sec.data()+offset,n);offset+=n;
    result.push_back(row);
  }
  return result;
}
void serve(){State&s=S();int listener;{std::lock_guard<std::mutex>g(s.mu);listener=s.listenfd;}
 while(!s.stopping.load()){
  fd_set r;FD_ZERO(&r);FD_SET(listener,&r);timeval tv{0,300000};if(select(listener+1,&r,nullptr,nullptr,&tv)<=0)continue;
  sockaddr_in from{};socklen_t n=sizeof(from);int fd=accept(listener,reinterpret_cast<sockaddr*>(&from),&n);if(fd<0)continue;
  if(ntohl(from.sin_addr.s_addr)!=INADDR_LOOPBACK){close(fd);continue;}
  {std::lock_guard<std::mutex>g(s.mu);s.clientfd=fd;}
  timeval sendtimeout{2,0};setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&sendtimeout,sizeof(sendtimeout));
  char req[1024];ssize_t got=recv(fd,req,sizeof(req)-1,0);
  if(got<=0){{std::lock_guard<std::mutex>g(s.mu);if(s.clientfd==fd)s.clientfd=-1;}close(fd);continue;}req[got]=0;
  if(std::string(req).find("GET /live.ts ")!=0){const std::string fail="HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
    send_all(fd,reinterpret_cast<const uint8_t*>(fail.data()),fail.size());
    {std::lock_guard<std::mutex>g(s.mu);if(s.clientfd==fd)s.clientfd=-1;}close(fd);continue;}
  const std::string hdr="HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nCache-Control: no-store, no-transform\r\nConnection: close\r\n\r\n";
  if(!send_all(fd,reinterpret_cast<const uint8_t*>(hdr.data()),hdr.size())){
    {std::lock_guard<std::mutex>g(s.mu);if(s.clientfd==fd)s.clientfd=-1;}close(fd);continue;}
  std::string key;uint64_t cursor=0;std::vector<std::array<uint8_t,188>> prefix;
  {std::lock_guard<std::mutex>g(s.mu);key=s.selected;cursor=s.live.empty()?s.next_seq:(s.live.front().seq-1);
    for(const auto& pair:s.channels)if(pair.second.key==key){
       const Channel& c=pair.second;
       auto pat=psi_packets(0,selected_pat(c.tsid,c.sid,c.pmt));
       prefix.insert(prefix.end(),pat.begin(),pat.end());
       auto it=s.last_sections.find(c.pmt);
       if(it!=s.last_sections.end()){
         auto pmt=psi_packets(c.pmt,it->second);
         prefix.insert(prefix.end(),pmt.begin(),pmt.end());
       }
       break;}}
  for(const auto& p:prefix){if(!send_all(fd,p.data(),188))break;}
  while(!s.stopping.load()&&!key.empty()){
    std::vector<std::array<uint8_t,188>> batch;
    {std::unique_lock<std::mutex>lk(s.mu);s.cv.wait_for(lk,std::chrono::milliseconds(350),[&]{return s.stopping.load()||s.selected!=key||s.next_seq>cursor;});
      if(s.stopping.load()||s.selected!=key)break;
      for(const auto& p:s.live){if(p.seq>cursor){batch.push_back(p.data);cursor=p.seq;if(batch.size()>=128)break;}}}
    if(batch.empty())continue;
    for(const auto& p:batch){if(!send_all(fd,p.data(),188)){key.clear();break;}}
  }
  {std::lock_guard<std::mutex>g(s.mu);if(s.clientfd==fd)s.clientfd=-1;}
  shutdown(fd,SHUT_RDWR);close(fd);
 }
}
std::string event_json(const Event& e){
 std::ostringstream o;o<<"{\"title\":"<<quote(e.name)<<",\"description\":"<<quote(e.description)
   <<",\"start\":"<<quote(e.start?utc(e.start):"")
   <<",\"end\":"<<quote(e.end?utc(e.end):"")<<"}";return o.str();
}
std::string channel_json(const Channel&c){
 const int64_t now=std::time(nullptr);
 Event current{}, following{};
 for(const auto& it:c.events){const Event&e=it.second;
   if(e.start<=now&&now<e.end)current=e;
   if(e.start>now&&(!following.start||e.start<following.start))following=e;
 }
 if(!current.start && c.now.start<=now && now<c.now.end)current=c.now;
 if(!following.start && c.next.start>now)following=c.next;
 std::ostringstream o;o<<"{\"key\":"<<quote(c.key)<<",\"name\":"<<quote(c.name)
 <<",\"provider\":"<<quote(c.provider)<<",\"serviceId\":"<<c.sid<<",\"frequencyHz\":"<<c.frequency
 <<",\"bandwidthMHz\":"<<c.bandwidth<<",\"number\":"<<c.lcn<<",\"encrypted\":"<<(c.encrypted?"true":"false")
 <<",\"pmtPid\":"<<c.pmt<<",\"streamCount\":"<<c.streams.size()
 <<",\"now\":"<<event_json(current)<<",\"next\":"<<event_json(following)<<",\"events\":[";
 bool first=true;
 for(const auto& it:c.events){const Event&e=it.second;
   if(e.end<now-3600||e.start>now+7*86400)continue;
   if(!first)o<<",";first=false;o<<event_json(e);
 }
 o<<"]}";
 return o.str();}
} // namespace
void DisconnectImpl(const char* reason);
bool Connect(const std::string&host,int control,int receive){State&s=S();
 in_addr ip{};if(inet_pton(AF_INET,host.c_str(),&ip)!=1||ntohl(ip.s_addr)==INADDR_ANY||ntohl(ip.s_addr)==INADDR_LOOPBACK||
     control<=0||control>65535||receive<=0||receive>65535){set_error("Use an IPv4 tuner address and valid UDP ports");return false;}
 DisconnectImpl("reconnect");s.stopping=false;
 int r=socket(AF_INET,SOCK_DGRAM,0),w=socket(AF_INET,SOCK_DGRAM,0);
 if(r<0||w<0){if(r>=0)close(r);if(w>=0)close(w);set_error("Could not open native UDP sockets: "+std::to_string(errno));return false;}
 int reuse=1;setsockopt(r,SOL_SOCKET,SO_REUSEADDR,&reuse,sizeof(reuse));int cap=8*1024*1024;setsockopt(r,SOL_SOCKET,SO_RCVBUF,&cap,sizeof(cap));
 timeval timeout{0,250000};setsockopt(r,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
 sockaddr_in bind_addr{};bind_addr.sin_family=AF_INET;bind_addr.sin_port=htons(uint16_t(receive));
 if(bind(r,reinterpret_cast<sockaddr*>(&bind_addr),sizeof(bind_addr))!=0){int e=errno;close(r);close(w);set_error("Could not listen on T405 UDP port "+std::to_string(receive)+" (errno "+std::to_string(e)+")");return false;}
 int listener=socket(AF_INET,SOCK_STREAM,0);int httpport=0;
 if(listener>=0){setsockopt(listener,SOL_SOCKET,SO_REUSEADDR,&reuse,sizeof(reuse));sockaddr_in local{};
  local.sin_family=AF_INET;local.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
  if(bind(listener,reinterpret_cast<sockaddr*>(&local),sizeof(local))!=0||listen(listener,1)!=0){close(listener);listener=-1;}
  else{socklen_t len=sizeof(local);if(getsockname(listener,reinterpret_cast<sockaddr*>(&local),&len)==0)httpport=ntohs(local.sin_port);}}
 {std::lock_guard<std::mutex>g(s.mu);s.host=host;s.control_port=control;s.receive_port=receive;
  s.recvfd=r;s.sendfd=w;s.listenfd=listener;s.http_port=httpport;s.error.clear();s.replied=false;
  s.datagrams=0;s.ts_packets=0;s.siano_messages=0;s.queue_drops=0;
  s.control_sends=0;s.last_control_error.clear();s.last_disconnect_reason.clear();s.receive_faulted=false;
  s.battery_requests=0;s.battery_replies=0;s.battery_raw_words.clear();
  s.battery_payload_bytes=0;s.battery_raw_hex.clear();s.battery_requested={};s.battery_received={};
  s.frequency=0;s.bandwidth=8;s.signal=false;s.signal_known=false;
  s.last_recv={};s.last_siano_reply={};s.last_ts_recv={};
  s.pmts.clear();s.requested_pmts.clear();s.assembly.clear();s.last_sections.clear();s.pending_key.clear();s.pending_sid=0;s.pending_pmt=0;s.last_pmt_request={};s.last_pat_received={};s.last_pmt_received={};s.pat_sections=0;s.pmt_sections=0;s.pmt_requests=0;s.pmt_retries=0;s.stream_pid_requests=0;
  s.eit_pf_sections=0;s.eit_schedule_sections=0;s.eit_programmes=0;
  s.selected.clear();s.selected_pids.clear();s.selected_media_pids.clear();s.live.clear();s.next_seq=0;
  s.last_selected_media={};s.last_recovery={};s.selected_media_packets=0;
  s.recovery_attempts=0;s.recovery_successes=0;s.recovery_streak=0;s.recovery_pending=false;s.recovery_action="Idle";
  s.channels.clear();s.message="Native Wi-Fi TV receiver listening on UDP "+std::to_string(receive);}
 s.reader=std::thread(receive_loop);if(listener>=0)s.server=std::thread(serve);
 return true;
}
void DisconnectImpl(const char* reason){State&s=S();s.stopping=true;s.scan_stop=true;
 {std::lock_guard<std::mutex>g(s.mu);s.selected.clear();s.cv.notify_all();
  if(s.clientfd>=0)shutdown(s.clientfd,SHUT_RDWR);
  if(s.listenfd>=0)shutdown(s.listenfd,SHUT_RDWR);}
 if(s.scan.joinable())s.scan.join();
 if(s.reader.joinable())s.reader.join();
 if(s.server.joinable())s.server.join();
 {std::lock_guard<std::mutex>g(s.mu);for(int*fd:{&s.recvfd,&s.sendfd,&s.listenfd}){if(*fd>=0)close(*fd);*fd=-1;}
    s.clientfd=-1;s.http_port=0;s.scanning=false;s.selected_pids.clear();s.selected_media_pids.clear();s.live.clear();
    s.last_selected_media={};s.last_recovery={};s.selected_media_packets=0;
    s.recovery_streak=0;s.recovery_pending=false;s.recovery_action="Idle";
    s.channels.clear();s.pmts.clear();s.requested_pmts.clear();s.assembly.clear();s.last_sections.clear();s.pending_key.clear();s.pending_sid=0;s.pending_pmt=0;s.last_pmt_request={};s.last_pat_received={};s.last_pmt_received={};s.pat_sections=0;s.pmt_sections=0;s.pmt_requests=0;s.pmt_retries=0;s.stream_pid_requests=0;
  s.eit_pf_sections=0;s.eit_schedule_sections=0;s.eit_programmes=0;
    s.lcn_by_sid.clear();s.replied=false;s.signal=false;s.signal_known=false;
    s.frequency=0;s.bandwidth=8;s.last_message=0;s.last_recv={};s.last_siano_reply={};s.last_ts_recv={};
    s.datagrams=0;s.ts_packets=0;s.siano_messages=0;s.control_sends=0;s.queue_drops=0;
    s.last_control_error.clear();s.receive_faulted=false;
    s.battery_requests=0;s.battery_replies=0;s.battery_raw_words.clear();
    s.battery_payload_bytes=0;s.battery_raw_hex.clear();s.battery_requested={};s.battery_received={};
    s.last_disconnect_reason=reason;s.message=std::string("Disconnected (")+reason+")";}}
void Disconnect(){DisconnectImpl("manual");}
int ControlSocketFd(){State&s=S();std::lock_guard<std::mutex>g(s.mu);return s.sendfd;}
bool Probe(){bool ok=send_msg(msg(kVersionReq,{0}));
  State&s=S();std::lock_guard<std::mutex>g(s.mu);
  if(ok && !s.replied)s.message="T405 version probe sent; awaiting tuner reply";
  else if(s.recvfd<0){s.error="Cannot probe T405: UDP receiver is disconnected";s.message=s.error;}
  return ok;}
bool Tune(int frequency,int bandwidth){if(frequency<42000000||frequency>870000000||(bandwidth!=6&&bandwidth!=7&&bandwidth!=8))return false;
 State&s=S();{std::lock_guard<std::mutex>g(s.mu);if(s.recvfd<0)return false;reset_frequency(s,frequency,bandwidth);}
 int bw=bandwidth==8?0:bandwidth==7?1:2;
 bool sent=send_msg(msg(kTuneReq,{uint32_t(frequency),uint32_t(bw),12000000}));
 for(int p:kSiPids)add_pid(p);
 send_msg(msg(kStatsReq,{0}));return sent;}
bool Scan(const std::string&profile){if(profile!="uk_freeview"&&profile!="eu_uhf")return false;
 State&s=S();{std::lock_guard<std::mutex>g(s.mu);if(s.scanning||s.recvfd<0||!s.selected.empty())return false;
   s.scanning=true;s.scan_progress=0;s.scan_total=profile=="uk_freeview"?28:49;s.scan_found=0;
   s.channels.clear();s.message="Scanning DVB-T multiplexes";}
 if(s.scan.joinable())s.scan.join();
 s.scan_stop=false;s.scan=std::thread(scan_loop,profile);return true;}
void CancelScan(){S().scan_stop=true;}
std::string StatusJson(){State&s=S();std::lock_guard<std::mutex>g(s.mu);std::ostringstream o;
 o<<"{\"connected\":"<<(s.recvfd>=0&&!s.receive_faulted.load()?"true":"false")<<",\"host\":"<<quote(s.host)
 <<",\"message\":"<<quote(s.message)<<",\"error\":"<<quote(s.error)
 <<",\"lastDisconnectReason\":"<<quote(s.last_disconnect_reason)
 <<",\"controlSends\":"<<s.control_sends
 <<",\"lastControlError\":"<<quote(s.last_control_error)
 <<",\"lastSianoReplyAgeMs\":"<<(s.siano_messages.load()>0?std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now()-s.last_siano_reply).count():-1)
 <<",\"lastTsAgeMs\":"<<(s.ts_packets.load()>0?std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now()-s.last_ts_recv).count():-1)
 <<",\"deviceReplied\":"<<(s.replied?"true":"false")<<",\"datagrams\":"<<s.datagrams.load()
 <<",\"tsPackets\":"<<s.ts_packets.load()<<",\"sianoMessages\":"<<s.siano_messages.load()
 <<",\"lastMessage\":"<<s.last_message<<",\"frequencyHz\":"<<s.frequency
 <<",\"signalKnown\":"<<(s.signal_known?"true":"false")<<",\"signalLocked\":"<<(s.signal?"true":"false")
 <<",\"scanning\":"<<(s.scanning?"true":"false")<<",\"scanProgress\":"<<s.scan_progress
 <<",\"scanTotal\":"<<s.scan_total<<",\"scanFound\":"<<s.scan_found
 <<",\"channels\":"<<s.channels.size()<<",\"streamDrops\":"<<s.queue_drops
   <<",\"selectedMediaPackets\":"<<s.selected_media_packets
   <<",\"selectedMediaAgeMs\":"<<(s.selected_media_packets>0?std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now()-s.last_selected_media).count():-1)
   <<",\"recoveryAttempts\":"<<s.recovery_attempts
   <<",\"recoverySuccesses\":"<<s.recovery_successes
   <<",\"recoveryStreak\":"<<s.recovery_streak
   <<",\"recoveryAction\":"<<quote(s.recovery_action)
 <<",\"lastPacketAgeMs\":"<<(s.datagrams.load()>0?std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now()-s.last_recv).count():-1)
 <<",\"pendingService\":"<<quote(s.pending_key)
 <<",\"pendingPmtPid\":"<<s.pending_pmt
 <<",\"patSections\":"<<s.pat_sections
 <<",\"pmtSections\":"<<s.pmt_sections
 <<",\"eitNowNextSections\":"<<s.eit_pf_sections
 <<",\"eitScheduleSections\":"<<s.eit_schedule_sections
 <<",\"eitProgrammeEntries\":"<<s.eit_programmes
 <<",\"pmtRequests\":"<<s.pmt_requests
 <<",\"pmtRetries\":"<<s.pmt_retries
 <<",\"elementaryPidRequests\":"<<s.stream_pid_requests
 <<",\"lastPatAgeMs\":"<<(s.pat_sections?std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now()-s.last_pat_received).count():-1)
 <<",\"lastPmtAgeMs\":"<<(s.pmt_sections?std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::steady_clock::now()-s.last_pmt_received).count():-1)
 <<",\"selected\":"<<quote(s.selected)
 <<",\"httpPort\":"<<s.http_port<<"}";return o.str();}
std::string ChannelsJson(){State&s=S();std::lock_guard<std::mutex>g(s.mu);std::vector<Channel>channels;
 for(const auto&v:s.channels)channels.push_back(v.second);
 std::sort(channels.begin(),channels.end(),[](const Channel&a,const Channel&b){if(a.lcn!=b.lcn&&a.lcn>0&&b.lcn>0)return a.lcn<b.lcn;return a.name<b.name;});
 std::ostringstream o;o<<"[";for(std::size_t i=0;i<channels.size();++i){if(i)o<<",";o<<channel_json(channels[i]);}o<<"]";return o.str();}
// Restore the user's last known DVB lineup so tapping a saved channel can
// retune its recorded multiplex after an application restart. Does not invent
// services or bypass PAT/PMT acquisition before playback. Input rows are
// tab-separated numbers and sanitized display text, limited to 600 services.
int RestoreChannels(const std::string& payload){
 if(payload.size()>350000)return 0;
 State&s=S();std::lock_guard<std::mutex>g(s.mu);
 if(s.scanning)return 0;
 std::istringstream input(payload);std::string line;int added=0,lines=0;
 while(std::getline(input,line) && ++lines<=600){
   std::vector<std::string> cols;std::istringstream row(line);std::string part;
   while(std::getline(row,part,'\t') && cols.size()<10)cols.push_back(part);
   // A blank provider is common and must not reject a valid Freeview service.
   if(cols.size()==8)cols.emplace_back();
   if(cols.size()!=9)continue;
   try {
     const int onid=std::stoi(cols[0]),tsid=std::stoi(cols[1]),sid=std::stoi(cols[2]);
     const int frequency=std::stoi(cols[3]),bandwidth=std::stoi(cols[4]),lcn=std::stoi(cols[5]);
     const int encrypted=std::stoi(cols[6]);
     if(onid<0||onid>65535||tsid<0||tsid>65535||sid<=0||sid>65535||
       frequency<42000000||frequency>870000000||(bandwidth!=6&&bandwidth!=7&&bandwidth!=8)||
       lcn<0||lcn>9999||cols[7].empty()||cols[7].size()>180||cols[8].size()>180)continue;
     const std::string key=std::to_string(onid)+":"+std::to_string(tsid)+":"+std::to_string(sid);
     auto it=s.channels.find(key);
     if(it!=s.channels.end())continue; // Never override fresh broadcast data.
     Channel c;c.key=key;c.onid=onid;c.tsid=tsid;c.sid=sid;c.frequency=frequency;
     c.bandwidth=bandwidth;c.lcn=lcn;c.encrypted=(encrypted!=0);
     c.name=cols[7];c.provider=cols[8];s.channels.emplace(key,std::move(c));++added;
   }catch(const std::exception&){continue;}
 }
 return added;
}
std::string Play(const std::string&key){
 State&s=S();int frequency=0,bw=8,sid=0;
 {
   std::lock_guard<std::mutex>g(s.mu);
   if(s.scanning||s.recvfd<0)return "";
   auto it=s.channels.find(key);
   if(it==s.channels.end()||it->second.encrypted)return "";
   frequency=it->second.frequency;bw=it->second.bandwidth;sid=it->second.sid;
 }
 if(!frequency||sid<=0)return "";
 bool retune=false;
 {std::lock_guard<std::mutex>g(s.mu);retune=frequency!=s.frequency;}
 if(retune){
   // Keep discovered channel metadata across frequencies, but reset PSI/PMT
   // from the previous multiplex. The UI will retry this request.
   if(!Tune(frequency,bw))return "";
 }
 int desired_pmt=0;bool send_pmt=false,send_pat=false;
 std::set<int> selected;
 int pcr=0x1fff;
 bool ready=false;
 {
   std::lock_guard<std::mutex>g(s.mu);
   s.pending_key=key;s.pending_sid=sid;
   auto it=s.channels.find(key);
   if(it==s.channels.end())return "";
   Channel& c=it->second;
   auto pat=s.pmts.find(sid);
   if(pat!=s.pmts.end())c.pmt=pat->second;
   desired_pmt=c.pmt;
   s.pending_pmt=desired_pmt;
   auto current=std::chrono::steady_clock::now();
   if(desired_pmt>0 && desired_pmt<0x1fff){
     // Send the selected programme's PID request instead of installing every
     // PMT in the mux. Retry periodically if sections do not arrive.
     bool first=s.requested_pmts.insert(desired_pmt).second;
     bool expired=(!first && (s.last_pmt_request==std::chrono::steady_clock::time_point{} ||
       current-s.last_pmt_request>std::chrono::milliseconds(1700)));
     if(first||expired){
        send_pmt=true;++s.pmt_requests;if(expired)++s.pmt_retries;
        s.last_pmt_request=current;
     }
   } else if(s.pat_sections==0){
     // PAT is already included in the basic SI filter set, but if the
     // multiplex has not answered yet, we can reissue its request.
     if(s.last_pmt_request==std::chrono::steady_clock::time_point{}||
        current-s.last_pmt_request>std::chrono::milliseconds(1700)){
          send_pat=true;s.last_pmt_request=current;
     }
   }
   pcr=c.pcr;selected=c.streams;
   ready=desired_pmt>0 && !selected.empty() && s.http_port!=0 &&
         s.last_sections.count(desired_pmt)>0;
   if(ready){
      selected.insert(0);selected.insert(desired_pmt);
      if(pcr>0&&pcr<0x1fff)selected.insert(pcr);
      s.selected=key;s.selected_pids=selected;s.selected_media_pids=c.streams;
      s.selected_media_packets=0;s.last_selected_media={};
      s.recovery_streak=0;s.recovery_pending=false;s.recovery_action="Idle";
      s.pending_key.clear();s.pending_sid=0;
      s.live.clear();++s.next_seq;s.cv.notify_all();
      s.stream_pid_requests+=selected.size();
   }
 }
 if(send_pat)add_pid(0);
 if(send_pmt)add_pid(desired_pmt);
 if(!ready)return "";
 for(int p:selected)add_pid(p);
 int port;{std::lock_guard<std::mutex>g(s.mu);port=s.http_port;}
 return "http://127.0.0.1:"+std::to_string(port)+"/live.ts";
}
// Invoked by the Android Guardian only after verified active media has gone
// silent. Preserve the selected service, HTTP listener, MPV client and lineup.
// First restore filters; only on a later still-stalled check retune the SAME
// multiplex without calling Tune(), which would clear the player session.
bool RecoverStream(){
 State&s=S();std::set<int> filters;int frequency=0,bandwidth=8;bool hard=false;
 const auto now=std::chrono::steady_clock::now();
 {
   std::lock_guard<std::mutex>g(s.mu);
   if(s.stopping.load()||s.scanning||s.recvfd<0||s.sendfd<0||s.receive_faulted.load()||
      s.selected.empty()||s.selected_media_packets==0||s.last_selected_media==std::chrono::steady_clock::time_point{}||
      now-s.last_selected_media<std::chrono::seconds(12)||
      (s.last_recovery!=std::chrono::steady_clock::time_point{}&&now-s.last_recovery<std::chrono::seconds(24)))return false;
   // Cap recovery bursts: after four attempts require 90s of cooldown.
   if(s.recovery_streak>=4){
      if(now-s.last_recovery<std::chrono::seconds(90))return false;
      s.recovery_streak=0;
   }
   hard=(s.recovery_streak%2)==1;
   frequency=s.frequency;bandwidth=s.bandwidth;
   if(frequency<42000000||frequency>870000000)return false;
   filters=s.selected_pids;
   for(int pid:kSiPids)filters.insert(pid);
   s.last_recovery=now;++s.recovery_attempts;++s.recovery_streak;
   s.recovery_pending=true;
   s.recovery_action=hard?"Retuning selected multiplex; restoring filters":"Reissuing active PID filters";
 }
 bool ok=true;
 if(hard){
   const int bw=bandwidth==8?0:bandwidth==7?1:2;
   ok=send_msg(msg(kTuneReq,{uint32_t(frequency),uint32_t(bw),12000000}))&&ok;
 }
 for(int p:filters)ok=send_msg(msg(kAddPidReq,{uint32_t(p)}))&&ok;
 ok=send_msg(msg(kStatsReq,{0}))&&ok;
 if(!ok){std::lock_guard<std::mutex>g(s.mu);s.recovery_action="Recovery requests failed; inspect tuner route";}
 return ok;
}
void StopPlay(){State&s=S();std::lock_guard<std::mutex>g(s.mu);s.selected.clear();s.pending_key.clear();s.pending_sid=0;s.pending_pmt=0;s.selected_pids.clear();s.selected_media_pids.clear();
 s.last_selected_media={};s.selected_media_packets=0;s.recovery_streak=0;s.recovery_pending=false;s.recovery_action="Stopped";
 s.live.clear();++s.next_seq;s.cv.notify_all();
 if(s.clientfd>=0)shutdown(s.clientfd,SHUT_RDWR);}
} // namespace pm_live_tv
