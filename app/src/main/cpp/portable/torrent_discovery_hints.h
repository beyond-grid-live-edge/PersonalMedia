#pragma once
#include <algorithm>
#include <cctype>
#include <string>
#include <unordered_set>
#include <vector>

namespace pm_torrent {
// A Stremio source hint of `dht:<node ID/info hash>` is NOT a DNS host:port.
// libtorrent already queries DHT by torrent infohash. Do not reinterpret it
// as a socket address; retain its count only for transparent diagnostics.
struct DiscoveryHints {
  std::vector<std::string> trackers;
  unsigned dhtHints = 0;
  unsigned rejected = 0;
  unsigned httpCount = 0;
  unsigned httpsCount = 0;
  unsigned udpCount = 0;
};
inline bool DiscoveryStartsWithCI(const std::string& s, const char* prefix) {
  size_t i=0;
  for(;prefix[i];++i) {
    if(i>=s.size() || std::tolower(static_cast<unsigned char>(s[i])) != prefix[i]) return false;
  }
  return true;
}
inline std::string TrimDiscovery(const std::string& s) {
  const size_t start=s.find_first_not_of(" \t\r");
  if(start==std::string::npos)return "";
  const size_t end=s.find_last_not_of(" \t\r");
  return s.substr(start,end-start+1);
}
inline DiscoveryHints ParseDiscoveryHints(const std::string& input) {
  DiscoveryHints out;
  std::unordered_set<std::string> seen;
  size_t pos=0;
  unsigned count=0;
  while(pos<input.size() && count<128) {
    size_t end=input.find('\n',pos);
    if(end==std::string::npos)end=input.size();
    std::string value=TrimDiscovery(input.substr(pos,end-pos));
    pos=end+1;
    ++count;
    if(value.empty())continue;
    if(value.size()>2048 || (value.find_first_of("\r\n\t")!=std::string::npos || value.find(char(0))!=std::string::npos)) {
      ++out.rejected;continue;
    }
    if(DiscoveryStartsWithCI(value,"tracker:"))value=value.substr(8);
    if(DiscoveryStartsWithCI(value,"dht:")) {++out.dhtHints;continue;}
    unsigned* counter=nullptr;
    if(DiscoveryStartsWithCI(value,"https://"))counter=&out.httpsCount;
    else if(DiscoveryStartsWithCI(value,"http://"))counter=&out.httpCount;
    else if(DiscoveryStartsWithCI(value,"udp://"))counter=&out.udpCount;
    else {++out.rejected;continue;}
    const auto colon=value.find("://");
    const auto host=value.substr(colon+3);
    // Reject empty hosts, embedded credentials, whitespace and ambiguous URLs.
    if(host.empty() || host[0]=='/' || host.find('@')!=std::string::npos ||
       host.find_first_of(" \t\r\n")!=std::string::npos) {
      ++out.rejected;continue;
    }
    // Normalize only the scheme; preserve case-sensitive tracker paths and query.
    std::transform(value.begin(),value.begin()+static_cast<std::ptrdiff_t>(colon),value.begin(),
      [](unsigned char c){return char(std::tolower(c));});
    if(seen.insert(value).second) {out.trackers.push_back(value);++*counter;}
  }
  return out;
}
}
