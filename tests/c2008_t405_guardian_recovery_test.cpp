// LTV-3: only recover a real selected-media stall, never replace MPV's
// loopback server or interrupt scanning/manual stop. Synthetic, no tuner.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
using namespace pm_live_tv;
int main(){
  State& s=S();
  auto rx=socket(AF_INET,SOCK_DGRAM,0), tx=socket(AF_INET,SOCK_DGRAM,0);
  assert(rx>=0&&tx>=0);
  sockaddr_in local{};local.sin_family=AF_INET;local.sin_addr.s_addr=htonl(INADDR_LOOPBACK);local.sin_port=0;
  assert(bind(rx,reinterpret_cast<sockaddr*>(&local),sizeof(local))==0);
  socklen_t len=sizeof(local);
  assert(getsockname(rx,reinterpret_cast<sockaddr*>(&local),&len)==0);
  {
    std::lock_guard<std::mutex>g(s.mu);
    s.recvfd=rx;s.sendfd=tx;s.host="127.0.0.1";s.control_port=ntohs(local.sin_port);
    s.frequency=530000000;s.bandwidth=8;s.selected="9018:4220:4220";
    s.selected_pids={0,100,0x345};s.selected_media_pids={0x345};
    s.selected_media_packets=25;
    s.last_selected_media=std::chrono::steady_clock::now()-std::chrono::seconds(13);
    s.http_port=40975;
    s.scanning=true;
  }
  assert(!RecoverStream());
  {std::lock_guard<std::mutex>g(s.mu);s.scanning=false;}
  assert(RecoverStream());
  assert(!RecoverStream()); // 24-second throttle
  assert(StatusJson().find("\"recoveryAttempts\":1")!=std::string::npos);
  assert(StatusJson().find("Reissuing active PID filters")!=std::string::npos);
  {
    std::lock_guard<std::mutex>g(s.mu);
    s.last_recovery-=std::chrono::seconds(25);
    s.last_selected_media-=std::chrono::seconds(25);
  }
  assert(RecoverStream()); // second stage retunes same mux, doesn't drop HTTP
  assert(StatusJson().find("\"recoveryAttempts\":2")!=std::string::npos);
  assert(StatusJson().find("Retuning selected multiplex")!=std::string::npos);
  {
    std::lock_guard<std::mutex>g(s.mu);
    assert(s.selected=="9018:4220:4220" && s.http_port==40975 && s.selected_pids.count(0x345));
    std::array<uint8_t,188> pkt{};
    pkt[0]=0x47;pkt[1]=0x03;pkt[2]=0x45;pkt[3]=0x10;
    packet(s,pkt.data());
    assert(s.recovery_streak==0 && s.recovery_successes==1);
  }
  assert(StatusJson().find("\"recoverySuccesses\":1")!=std::string::npos);
  StopPlay();
  assert(!RecoverStream()); // intentional stop cannot recover
  {std::lock_guard<std::mutex>g(s.mu);s.recvfd=-1;s.sendfd=-1;s.http_port=0;}
  close(rx);close(tx);
  std::cout<<"PASS guardian PID restore / staged retune / MPV continuity / cancellation\n";
}
