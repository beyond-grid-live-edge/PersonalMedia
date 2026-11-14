// C.20.0.3: native receiver status, reconnect, tuner-reply and explicit teardown.
// A local fake UDP tuner is used; no RF hardware is required.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
#include <thread>
using namespace pm_live_tv;
int main(){
    int fake=socket(AF_INET,SOCK_DGRAM,0);assert(fake>=0);
    sockaddr_in source{};source.sin_family=AF_INET;source.sin_port=0;
    assert(inet_pton(AF_INET,"127.0.0.2",&source.sin_addr)==1);
    assert(bind(fake,reinterpret_cast<sockaddr*>(&source),sizeof(source))==0);
    socklen_t n=sizeof(source);assert(getsockname(fake,reinterpret_cast<sockaddr*>(&source),&n)==0);
    int receive_port=48500+(getpid()%600);
    assert(Connect("127.0.0.2",ntohs(source.sin_port),receive_port));
    auto status=StatusJson();
    assert(status.find("\"connected\":true")!=std::string::npos);
    assert(status.find("\"deviceReplied\":false")!=std::string::npos);
    assert(Probe());
    status=StatusJson();
    assert(status.find("\"controlSends\":1")!=std::string::npos);
    assert(status.find("\"deviceReplied\":false")!=std::string::npos);
    // The transport is genuinely connected only after this version response.
    auto reply=msg(kVersionRes,{0});
    sockaddr_in dst{};dst.sin_family=AF_INET;dst.sin_port=htons(uint16_t(receive_port));
    inet_pton(AF_INET,"127.0.0.1",&dst.sin_addr);
    assert(sendto(fake,reply.data(),reply.size(),0,reinterpret_cast<sockaddr*>(&dst),sizeof(dst))==ssize_t(reply.size()));
    std::this_thread::sleep_for(std::chrono::milliseconds(100));
    status=StatusJson();
    assert(status.find("\"deviceReplied\":true")!=std::string::npos);
    assert(status.find("\"datagrams\":1")!=std::string::npos);
    // Another user Connect should replace (not leak) the native receiver.
    assert(Connect("127.0.0.2",ntohs(source.sin_port),receive_port));
    status=StatusJson();
    assert(status.find("\"connected\":true")!=std::string::npos);
    assert(status.find("\"deviceReplied\":false")!=std::string::npos);
    assert(status.find("\"datagrams\":0")!=std::string::npos);
    Disconnect();
    status=StatusJson();
    assert(status.find("\"connected\":false")!=std::string::npos);
    assert(status.find("\"lastDisconnectReason\":\"manual\"")!=std::string::npos);
    assert(status.find("\"datagrams\":0")!=std::string::npos);
    assert(status.find("\"channels\":0")!=std::string::npos);
    assert(ChannelsJson()=="[]");
    assert(!Probe());
    assert(StatusJson().find("Cannot probe T405: UDP receiver is disconnected")!=std::string::npos);
    close(fake);
    std::cout<<"PASS C20.0.3 native receiver connection, probe, reconnect and explicit teardown\n";
}
