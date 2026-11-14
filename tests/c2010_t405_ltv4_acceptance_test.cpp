// LTV-4 release checks: user-facing TV status without experimental battery fields,
// persistent channels, and a bounded receiver lifecycle.
#include "../app/src/main/cpp/portable/native_live_tv.cpp"
#include <cassert>
#include <iostream>
using namespace pm_live_tv;
int main(){
  const auto clean=StatusJson();
  assert(clean.find("\"batteryPercent\"")==std::string::npos);
  assert(clean.find("\"batteryRequests\"")==std::string::npos);
  assert(clean.find("\"batteryRawWords\"")==std::string::npos);
  assert(!clean.empty() && clean.front()=='{' && clean.back()=='}');
  assert(RestoreChannels("9018\t4220\t4220\t530000000\t8\t21\t0\t5USA\t") == 1);
  assert(RestoreChannels("9018\t4220\t4220\t530000000\t8\t21\t0\t5USA\t") == 0);
  const auto lineup=ChannelsJson();
  assert(lineup.find("5USA")!=std::string::npos);
  assert(lineup.find("530000000")!=std::string::npos);
  assert(StatusJson().find("\"connected\":false")!=std::string::npos);
  Disconnect();
  std::cout<<"LTV4 FINAL smoke: dormant battery, saved DVB identities, no phantom connection PASS\n";
}
