// Exercises targeted PMT requests and a PMT spanning multiple TS packets.
// No physical tuner or proprietary library is needed.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
#include <thread>
using namespace pm_live_tv;

static void deliver_packet(int fd,int port,const std::array<uint8_t,188>& ts){
 auto wire=msg(kSianoData);
 wire[4]=uint8_t((8+188)&0xff);wire[5]=uint8_t((8+188)>>8);
 wire.insert(wire.end(),ts.begin(),ts.end());
 sockaddr_in dest{};dest.sin_family=AF_INET;
 dest.sin_port=htons(uint16_t(port));
 assert(inet_pton(AF_INET,"127.0.0.1",&dest.sin_addr)==1);
 assert(sendto(fd,wire.data(),wire.size(),0,reinterpret_cast<sockaddr*>(&dest),sizeof(dest))==(ssize_t)wire.size());
}
static void deliver_section(int fd,int port,int pid,const std::vector<uint8_t>& section){
 for(const auto& packet:psi_packets(pid,section))deliver_packet(fd,port,packet);
}
int main(){
 const int fake=socket(AF_INET,SOCK_DGRAM,0);assert(fake>=0);
 sockaddr_in sender{};sender.sin_family=AF_INET;sender.sin_port=0;
 assert(inet_pton(AF_INET,"127.0.0.2",&sender.sin_addr)==1);
 assert(bind(fake,reinterpret_cast<sockaddr*>(&sender),sizeof(sender))==0);
 socklen_t length=sizeof(sender);
 assert(getsockname(fake,reinterpret_cast<sockaddr*>(&sender),&length)==0);
 const int recvPort=53000+getpid()%400;
 assert(Connect("127.0.0.2",ntohs(sender.sin_port),recvPort));
 assert(Tune(474000000,8));
 const auto pat=selected_pat(1,100,0x100);
 // This test also advertises another service, but only SID 100 is selected.
 auto pat_two=pat;
 pat_two.insert(pat_two.begin()+12,{0,101,0xE1,0x20});
 pat_two[2]=0x11;
 pat_two.resize(pat_two.size()-4);
 uint32_t crc=mpeg_crc(pat_two.data(),pat_two.size());
 for(int i=24;i>=0;i-=8)pat_two.push_back(uint8_t(crc>>i));
 const std::vector<uint8_t> sdt={0x42,0xF0,31,0,1,0xC1,0,0,0,1,0xFF,
    0,100,0xff,0,13,0x48,11,1,4,'T','e','s','t',4,'N','e','w','s',0,0,0,0};
 deliver_section(fake,recvPort,0,pat_two);
 deliver_section(fake,recvPort,0x11,sdt);
 std::this_thread::sleep_for(std::chrono::milliseconds(110));
 assert(ChannelsJson().find("\"name\":\"News\"")!=std::string::npos);
 // No PMT available yet; a Play retry must request PID 0x100 without
 // requesting 0x120 for the other service in the multiplex.
 assert(Play("1:1:100").empty());
 assert(StatusJson().find("\"pendingPmtPid\":256")!=std::string::npos);
 assert(StatusJson().find("\"pmtRequests\":1")!=std::string::npos);
 assert(S().requested_pmts.count(0x100)==1);
 assert(S().requested_pmts.count(0x120)==0);
 // Create a PMT with video + many audio/data streams to exceed 183 bytes.
 std::vector<uint8_t> pmt={2,0xb0,0,0,100,0xc1,0,0,0xe1,1,0xf0,0};
 for(int i=0;i<40;++i){int streamPid=0x101+i;
   pmt.push_back(i==0?0x1b:0x0f);
   pmt.push_back(uint8_t(0xe0|((streamPid>>8)&0x1f)));
   pmt.push_back(uint8_t(streamPid));pmt.push_back(0xf0);pmt.push_back(0);
 }
 int sectionLength=int(pmt.size())-3+4;
 pmt[1]=uint8_t(0xb0|((sectionLength>>8)&0x0f));pmt[2]=uint8_t(sectionLength);
 crc=mpeg_crc(pmt.data(),pmt.size());
 for(int i=24;i>=0;i-=8)pmt.push_back(uint8_t(crc>>i));
 const auto pieces=psi_packets(0x100,pmt);
 assert(pieces.size()>=2);
 deliver_packet(fake,recvPort,pieces[0]);
 std::this_thread::sleep_for(std::chrono::milliseconds(110));
 assert(Play("1:1:100").empty()); // incomplete PMT cannot start playback
 for(std::size_t i=1;i<pieces.size();++i)deliver_packet(fake,recvPort,pieces[i]);
 std::this_thread::sleep_for(std::chrono::milliseconds(110));
 auto channels=ChannelsJson();
 assert(channels.find("\"streamCount\":40")!=std::string::npos);
 auto url=Play("1:1:100");
 assert(url.find("http://127.0.0.1:")==0);
 assert(StatusJson().find("\"pmtSections\":1")!=std::string::npos);
 // Validate constructed programme-specific PAT and full PMT prefix.
 auto expectedPat=selected_pat(1,100,0x100);
 assert(expectedPat.size()==16);
 assert(mpeg_crc(expectedPat.data(),expectedPat.size())==0);
 const auto pmtPrefix=psi_packets(0x100,S().last_sections[0x100]);
 assert(pmtPrefix.size()>=2);
 int first=std::stoi(url.substr(std::string("http://127.0.0.1:").size()));
 int client=socket(AF_INET,SOCK_STREAM,0);assert(client>=0);
 sockaddr_in addr{};addr.sin_family=AF_INET;addr.sin_port=htons(uint16_t(first));
 addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
 assert(connect(client,reinterpret_cast<sockaddr*>(&addr),sizeof(addr))==0);
 const char* request="GET /live.ts HTTP/1.1\r\nHost: localhost\r\n\r\n";
 assert(send(client,request,strlen(request),0)==ssize_t(strlen(request)));
 std::array<char,4096> response{};
 const auto n=recv(client,response.data(),response.size(),0);
 assert(n>0);
 assert(std::string(response.data(),std::size_t(n)).find("200 OK")!=std::string::npos);
 StopPlay();shutdown(client,SHUT_RDWR);close(client);
 Disconnect();close(fake);
 std::cout<<"PASS targeted PMT filter, multi-packet PMT, 40 stream PIDs, service PAT prefix\n";
}
