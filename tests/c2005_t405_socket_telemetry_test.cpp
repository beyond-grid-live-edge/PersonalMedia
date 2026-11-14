// Synthetic receiver test: confirms network-bind descriptor and real reply/TS ages.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
#include <thread>
using namespace pm_live_tv;
int main() {
    assert(ControlSocketFd() == -1);
    const int fake = socket(AF_INET, SOCK_DGRAM, 0); assert(fake >= 0);
    sockaddr_in bound{}; bound.sin_family=AF_INET; bound.sin_port=0;
    assert(inet_pton(AF_INET,"127.0.0.2",&bound.sin_addr)==1);
    assert(bind(fake,reinterpret_cast<sockaddr*>(&bound),sizeof(bound))==0);
    socklen_t len=sizeof(bound); assert(getsockname(fake,reinterpret_cast<sockaddr*>(&bound),&len)==0);
    const int receive_port=51000+(getpid()%500);
    assert(Connect("127.0.0.2",ntohs(bound.sin_port),receive_port));
    assert(ControlSocketFd()>=0);
    auto s=StatusJson();
    assert(s.find("\"deviceReplied\":false")!=std::string::npos);
    assert(s.find("\"lastSianoReplyAgeMs\":-1")!=std::string::npos);
    assert(s.find("\"lastTsAgeMs\":-1")!=std::string::npos);
    sockaddr_in dst{};dst.sin_family=AF_INET;dst.sin_port=htons(uint16_t(receive_port));
    assert(inet_pton(AF_INET,"127.0.0.1",&dst.sin_addr)==1);
    auto reply=msg(kVersionRes,{1,2});
    assert(sendto(fake,reply.data(),reply.size(),0,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==(ssize_t)reply.size());
    std::this_thread::sleep_for(std::chrono::milliseconds(80));
    s=StatusJson();
    assert(s.find("\"deviceReplied\":true")!=std::string::npos);
    assert(s.find("\"lastSianoReplyAgeMs\":-1")==std::string::npos);
    // Valid MPEG TS sync-byte packet, with no table or programme PID.
    std::vector<uint8_t> ts(188,0xff);ts[0]=0x47;ts[1]=0x1f;ts[2]=0xff;ts[3]=0x10;
    assert(sendto(fake,ts.data(),ts.size(),0,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==(ssize_t)ts.size());
    std::this_thread::sleep_for(std::chrono::milliseconds(80));
    s=StatusJson();
    assert(s.find("\"tsPackets\":1")!=std::string::npos);
    assert(s.find("\"lastTsAgeMs\":-1")==std::string::npos);
    Disconnect(); close(fake);
    assert(ControlSocketFd()==-1);
    assert(StatusJson().find("\"lastTsAgeMs\":-1")!=std::string::npos);
    std::cout<<"PASS native socket ownership, Siano-reply age, TS age and cleanup\n";
}
