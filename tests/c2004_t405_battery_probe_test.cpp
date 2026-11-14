// The original T405 APK's SmsLiteBatteryLevel_Req constructs a 24-byte packet.
// A fake UDP firmware verifies our request and response recording.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
#include <thread>
using namespace pm_live_tv;
int main(){
  // The production app no longer exposes a battery JNI function.
  // Keep this read-only protocol experiment as a standalone test only.
  assert(StatusJson().find("\"batteryPercent\"")==std::string::npos);
  const int fake=socket(AF_INET,SOCK_DGRAM,0);assert(fake>=0);
  sockaddr_in from{};from.sin_family=AF_INET;from.sin_port=0;
  assert(inet_pton(AF_INET,"127.0.0.2",&from.sin_addr)==1);
  assert(bind(fake,reinterpret_cast<sockaddr*>(&from),sizeof(from))==0);
  socklen_t sz=sizeof(from);assert(getsockname(fake,reinterpret_cast<sockaddr*>(&from),&sz)==0);
  timeval timeout{1,0};setsockopt(fake,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
  const int receive_port=49100+(getpid()%500);
  assert(Connect("127.0.0.2",ntohs(from.sin_port),receive_port));
  // No unsolicited battery requests are sent when connecting.
  timeval shortWait{0,200000};setsockopt(fake,SOL_SOCKET,SO_RCVTIMEO,&shortWait,sizeof(shortWait));
  std::array<uint8_t,128> noAuto{};
  assert(recvfrom(fake,noAuto.data(),noAuto.size(),0,nullptr,nullptr)<0);
  assert(request_battery());
  std::array<uint8_t,128>buf{};
  const auto n=recvfrom(fake,buf.data(),buf.size(),0,nullptr,nullptr);
  assert(n==24);assert(le16(buf.data())==750);assert(le16(buf.data()+4)==24);
  for(int i=8;i<24;++i)assert(buf[i]==0);
  assert(StatusJson().find("\"batteryPercent\"")==std::string::npos);
  {std::lock_guard<std::mutex>g(S().mu);assert(S().battery_requests==1);}
  // Simulate 751 with opaque firmware-specific payload; no percent inferred.
  const std::vector<uint32_t> opaque={17,800,1999,5};
  auto reply=msg(kBatteryRes,opaque);assert(reply.size()==24);
  sockaddr_in dst{};dst.sin_family=AF_INET;dst.sin_port=htons(uint16_t(receive_port));
  inet_pton(AF_INET,"127.0.0.1",&dst.sin_addr);
  // Spoofed battery responses from a different interface must not be accepted.
  int spoof=socket(AF_INET,SOCK_DGRAM,0);assert(spoof>=0);
  sockaddr_in bad{};bad.sin_family=AF_INET;bad.sin_port=0;
  assert(inet_pton(AF_INET,"127.0.0.3",&bad.sin_addr)==1);
  assert(bind(spoof,reinterpret_cast<sockaddr*>(&bad),sizeof(bad))==0);
  assert(sendto(spoof,reply.data(),reply.size(),0,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==24);
  std::this_thread::sleep_for(std::chrono::milliseconds(35));
  {std::lock_guard<std::mutex>g(S().mu);assert(S().battery_replies==0);}
  close(spoof);
  assert(sendto(fake,reply.data(),reply.size(),0,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==24);
  std::this_thread::sleep_for(std::chrono::milliseconds(80));
  {
    std::lock_guard<std::mutex>g(S().mu);
    assert(S().battery_replies==1);
    assert(S().battery_payload_bytes==16);
    assert((S().battery_raw_words==std::vector<uint32_t>{17,800,1999,5}));
  }
  assert(StatusJson().find("\"batteryPercent\"")==std::string::npos);
  assert(StatusJson().find("\"batteryRequests\"")==std::string::npos);
  Disconnect();
  {std::lock_guard<std::mutex>g(S().mu);assert(S().battery_replies==0);assert(S().battery_raw_words.empty());}
  assert(!request_battery());close(fake);
  std::cout<<"PASS LTV4 dormant battery lab: no automatic probe or app status exposure; manual research parser intact\n";
}
