// Native real EIT Now/Next and schedule regression across multiple time windows.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
using namespace pm_live_tv;
static void bcdTime(std::vector<uint8_t>& v, std::time_t t) {
    const int64_t sec = int64_t(t);
    const int64_t days = sec/86400;
    const int64_t rem = sec%86400;
    const int mjd = int(days + 40587);
    const auto toBcd=[](int x){return uint8_t((x/10)*16+(x%10));};
    v.push_back(uint8_t(mjd>>8)); v.push_back(uint8_t(mjd));
    v.push_back(toBcd(int(rem/3600)));v.push_back(toBcd(int(rem/60%60)));v.push_back(toBcd(int(rem%60)));
}
static void event(std::vector<uint8_t>& v,int id,std::time_t start,int minutes,const std::string& title){
    const auto toBcd=[](int x){return uint8_t((x/10)*16+(x%10));};
    v.push_back(uint8_t(id>>8));v.push_back(uint8_t(id));bcdTime(v,start);
    v.push_back(toBcd(minutes/60));v.push_back(toBcd(minutes%60));v.push_back(0);
    const int descLen = 3 + 1 + int(title.size()) + 1;
    v.push_back(0xf0);v.push_back(uint8_t(descLen+2));
    v.push_back(0x4d);v.push_back(uint8_t(descLen));
    v.insert(v.end(),{'e','n','g'});v.push_back(uint8_t(title.size()));
    v.insert(v.end(),title.begin(),title.end());v.push_back(0);
}
static std::vector<uint8_t> eit(int tid,int onid,int tsid,int sid,std::time_t at,int minutes,const std::string& title){
    std::vector<uint8_t> sec{uint8_t(tid),0xf0,0,uint8_t(sid>>8),uint8_t(sid),0xc1,0,0,
        uint8_t(tsid>>8),uint8_t(tsid),uint8_t(onid>>8),uint8_t(onid),0,uint8_t(tid)};
    event(sec,1,at,minutes,title);
    const size_t sectionLen = sec.size()+4-3;
    sec[1]=uint8_t(0xf0|((sectionLen>>8)&15));sec[2]=uint8_t(sectionLen);
    sec.insert(sec.end(),{0,0,0,0});return sec;
}
int main(){
    const auto now=std::time(nullptr);
    const auto day = now - (now % 86400);
    const int64_t at = now - (now%60) - 60;
    const std::string channel="1:2:3";
    assert(RestoreChannels("1\t2\t3\t530000000\t8\t4\t0\tChannel 4\t\n") == 1);
    State& s=S();
    { std::lock_guard<std::mutex> lock(s.mu);
        section(s,0x12,eit(0x4e,1,2,3,at,30,"Current EIT programme"));
        section(s,0x12,eit(0x4e,1,2,3,at+1800,60,"Following EIT programme"));
        section(s,0x12,eit(0x50,1,2,3,at+7200,60,"Later scheduled programme"));
        section(s,0x12,eit(0x50,1,2,3,at+14400,90,"Farther future programme"));
        assert(s.eit_pf_sections==2);
        assert(s.eit_schedule_sections==2);
        assert(s.eit_programmes==4);
        assert(s.channels.at(channel).events.size()==4);
    }
    const auto json = ChannelsJson();
    assert(json.find("Current EIT programme")!=std::string::npos);
    assert(json.find("Following EIT programme")!=std::string::npos);
    assert(json.find("Later scheduled programme")!=std::string::npos);
    assert(json.find("Farther future programme")!=std::string::npos);
    assert(json.find("\"now\":{\"title\":\"Current EIT programme\"")!=std::string::npos);
    assert(json.find("\"next\":{\"title\":\"Following EIT programme\"")!=std::string::npos);
    const auto status=StatusJson();
    assert(status.find("\"eitScheduleSections\":2")!=std::string::npos);
    std::cout << "EIT now/following + later schedule test PASS\n";
}
