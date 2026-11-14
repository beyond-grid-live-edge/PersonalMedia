#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
#include <thread>
using namespace pm_live_tv;

static void deliver(int fd,int port,uint16_t pid,const std::vector<uint8_t>& sec){
 std::array<uint8_t,188> ts{};ts.fill(0xff);ts[0]=0x47;ts[1]=0x40|uint8_t((pid>>8)&0x1f);ts[2]=uint8_t(pid);ts[3]=0x10;ts[4]=0;
 assert(sec.size()<=183);std::copy(sec.begin(),sec.end(),ts.begin()+5);
 auto wire=msg(kSianoData);wire[4]=uint8_t((8+188)&255);wire[5]=uint8_t((8+188)>>8);
 wire.insert(wire.end(),ts.begin(),ts.end());
 sockaddr_in dst{};dst.sin_family=AF_INET;dst.sin_port=htons(uint16_t(port));inet_pton(AF_INET,"127.0.0.1",&dst.sin_addr);
 assert(sendto(fd,wire.data(),wire.size(),0,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==ssize_t(wire.size()));
}
int main(){
 int fake=socket(AF_INET,SOCK_DGRAM,0);assert(fake>=0);
 sockaddr_in src{};src.sin_family=AF_INET;src.sin_port=0;inet_pton(AF_INET,"127.0.0.2",&src.sin_addr);
 assert(bind(fake,reinterpret_cast<sockaddr*>(&src),sizeof(src))==0);
 socklen_t len=sizeof(src);assert(getsockname(fake,reinterpret_cast<sockaddr*>(&src),&len)==0);
 int port=ntohs(src.sin_port);
 int recvPort=47001+(getpid()%1000);
 assert(Connect("127.0.0.2",port,recvPort));
 assert(Probe());assert(Tune(474000000,8));
 std::vector<uint8_t> buf(65536);
 timeval tv{0,300000};setsockopt(fake,SOL_SOCKET,SO_RCVTIMEO,&tv,sizeof(tv));
 int controls=0;
 for(int i=0;i<9;++i){ssize_t n=recvfrom(fake,buf.data(),buf.size(),0,nullptr,nullptr);if(n>=8)++controls;}
 assert(controls>=7); // probe,tune, SI PID filters, status
 const std::vector<uint8_t> pat={0,0xB0,0x0D,0,1,0xC1,0,0,0,100,0xE1,0,0,0,0,0};
 const std::vector<uint8_t> sdt={0x42,0xF0,31,0,1,0xC1,0,0,0,1,0xFF,
    0,100,0xff,0,13,0x48,11,1,4,'T','e','s','t',4,'N','e','w','s',0,0,0,0};
 const std::vector<uint8_t> pmt={2,0xB0,18,0,100,0xC1,0,0,0xE1,1,0xF0,0,0x1b,0xE1,1,0xF0,0,0,0,0,0};
 deliver(fake,recvPort,0,pat);deliver(fake,recvPort,0x11,sdt);
 deliver(fake,recvPort,0x100,pmt);
 std::this_thread::sleep_for(std::chrono::milliseconds(400));
 assert(StatusJson().find("\"tsPackets\":3")!=std::string::npos);
 assert(ChannelsJson().find("\"name\":\"News\"")!=std::string::npos);
 auto url=Play("1:1:100");if(url.empty())std::cerr<<ChannelsJson()<<"\n";
 assert(url.find("http://127.0.0.1:")==0);
 int httpPort=std::stoi(url.substr(std::string("http://127.0.0.1:").size()));
 int client=socket(AF_INET,SOCK_STREAM,0);assert(client>=0);
 sockaddr_in dst{};dst.sin_family=AF_INET;dst.sin_port=htons(uint16_t(httpPort));dst.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
 assert(connect(client,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==0);
 const char*get="GET /live.ts HTTP/1.1\r\nHost: localhost\r\n\r\n";
 assert(send(client,get,strlen(get),0)==ssize_t(strlen(get)));
 ssize_t n=recv(client,buf.data(),buf.size(),0);assert(n>0);
 std::string header(reinterpret_cast<char*>(buf.data()),std::size_t(n));
 assert(header.find("200 OK")!=std::string::npos);
 assert(header.find("video/mp2t")!=std::string::npos);
 deliver(fake,recvPort,0x101,std::vector<uint8_t>{0,1,2});
 std::this_thread::sleep_for(std::chrono::milliseconds(200));
 StopPlay();shutdown(client,SHUT_RDWR);close(client);
 Disconnect();close(fake);
 std::cout << "C20.0.1 UDP Siano tuner, channel scan, and MPEG-TS localhost handoff PASS\n";
}
