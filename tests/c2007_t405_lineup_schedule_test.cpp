// Regression: saved DVB lineup survives native lifecycle and Now/Next is
// recalculated at read time. No tuner and no internet needed.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <ctime>
#include <iostream>
using namespace pm_live_tv;
int main(){
    const std::string baseline="9018\t4220\t4220\t530000000\t8\t5\t0\t5USA\t\n"
        "1\t3\t27\t474000000\t8\t1\t0\tBBC ONE Scot\tBBC\n";
    assert(RestoreChannels(baseline)==2);
    assert(RestoreChannels(baseline)==0); // duplicate key deduplicated
    assert(ChannelsJson().find("5USA")!=std::string::npos);
    assert(ChannelsJson().find("BBC ONE Scot")!=std::string::npos);
    // A corrupt frequency and an invalid service identity must be ignored.
    assert(RestoreChannels("1\t1\t2\t0\t8\t10\t0\tBad\tProvider\n") == 0);
    {
      State& state=S();std::lock_guard<std::mutex> lock(state.mu);
      auto& ch=state.channels.at("9018:4220:4220");
      const int64_t now=std::time(nullptr);
      ch.events[now-3600]=Event{"Old show","expired",now-3600,now-1800};
      ch.events[now-300]=Event{"On air","real broadcast",now-300,now+1200};
      ch.events[now+1200]=Event{"Up next","real following",now+1200,now+3600};
    }
    const auto listing=ChannelsJson();
    assert(listing.find("\"now\":{\"title\":\"On air\"")!=std::string::npos);
    assert(listing.find("\"next\":{\"title\":\"Up next\"")!=std::string::npos);
    assert(listing.find("\"events\":[")!=std::string::npos);
    assert(listing.find("real broadcast")!=std::string::npos);
    assert(listing.find("\"now\":{\"title\":\"Old show\"")==std::string::npos);
    std::cout<<"T405 saved lineup / guide timestamps OK\n";
}
