// Host-side synthetic DVB packets and transport test; no tuner needed.
// Include the implementation so its private PSI/SI assembler is exercised.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>

int main(){
 using namespace pm_live_tv;
 State& s=S();s.frequency=474000000;s.bandwidth=8;
 const std::vector<uint8_t> pat={0,0xB0,0x0D,0,1,0xC1,0,0,0,100,0xE1,0,0,0,0,0};
 section(s,0,pat);
 assert(s.pmts[100]==256);
 const std::vector<uint8_t> sdt={0x42,0xF0,31,0,1,0xC1,0,0,0,1,0xFF,
    0,100,0xff,0,13,0x48,11,1,4,'T','e','s','t',4,'N','e','w','s',0,0,0,0};
 section(s,0x11,sdt);
 assert(s.channels.size()==1);
 assert(s.channels["1:1:100"].name=="News");
 assert(s.channels["1:1:100"].frequency==474000000);
 assert(s.channels["1:1:100"].pmt==256);
 const std::vector<uint8_t> pmt={2,0xB0,18,0,100,0xC1,0,0,0xE1,1,0xF0,0,0x1b,0xE1,1,0xF0,0,0,0,0,0};
 section(s,256,pmt);
 assert(s.channels["1:1:100"].pcr==257);
 assert(s.channels["1:1:100"].streams.count(257)==1);
 const std::vector<uint8_t> nit={0x40,0xf0,25,0,1,0xc1,0,0,0xf0,0,
   0xf0,12,0,1,0,1,0xf0,6,0x83,4,0,100,0xfc,1,0,0,0,0};
 section(s,0x10,nit);
 assert(s.channels["1:1:100"].lcn==1);
 const std::string channels=ChannelsJson();
 assert(channels.find("\"name\":\"News\"")!=std::string::npos);
 assert(channels.find("\"streamCount\":1")!=std::string::npos);
 assert(channels.find("\"number\":1")!=std::string::npos);
 // Transport packet assembler path (single PUSI section in 188-byte MPEG-TS).
 s.channels.clear();s.pmts.clear();s.assembly.clear();
 std::array<uint8_t,188> pkt{};pkt.fill(0xff);pkt[0]=0x47;pkt[1]=0x40;pkt[2]=0;pkt[3]=0x10;pkt[4]=0;
 std::copy(pat.begin(),pat.end(),pkt.begin()+5);
 parse_ts(s,pkt.data());
 assert(s.pmts[100]==256);
 assert(!Connect("not-an-ip",5002,5001));
 assert(!Tune(999999999,8));
 assert(!Scan("invalid-profile"));
 std::cout << "C20.0.1 native DVB PAT/PMT/SDT/NIT, TS assembler and tuner input tests PASS\n";
}
