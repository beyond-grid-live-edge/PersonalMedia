// C.18.1: MPV-compatible, loopback-only HTTP byte-range adapter.
// This never downloads torrents, does not fake missing data, and never returns
// a byte unless the existing source-bound SHA-1 cache validates it.
#include "mpv_range_server.h"
#include "streaming_torrent_session.h"
#include "torrent_media_type.h"
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <random>
#include <sstream>
#include <string>
#include <thread>
#include <vector>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <unistd.h>

namespace pm_torrent {
namespace {
constexpr size_t kChunk = 64 * 1024;
constexpr size_t kMaxRequest = 4096;
constexpr auto kWaitLimit = std::chrono::seconds(45);
struct Session {
  std::atomic<bool> closing{false};
  int listener = -1;
  uint64_t size = 0;
  std::string hash;
  int fileIndex = -1;
  TorrentMediaType mediaType;
  std::string token;
  std::thread acceptThread;
  std::mutex socketsLock;
  std::vector<int> sockets;
  std::atomic<uint64_t> demand{0};
  std::atomic<uint64_t> generation{0};
  std::atomic<uint64_t> requests{0};
  std::atomic<uint64_t> served{0};
  std::atomic<uint64_t> waiting{0};
  std::atomic<uint64_t> failures{0};
};
std::mutex globalLock;
std::shared_ptr<Session> current;

bool SendAll(int fd, const void* data, size_t size) {
  const char* p = static_cast<const char*>(data);
  while(size > 0) {
    const ssize_t written = send(fd, p, size, MSG_NOSIGNAL);
    if(written < 0 && errno == EINTR) continue;
    if(written <= 0) return false;
    p += written;
    size -= static_cast<size_t>(written);
  }
  return true;
}
bool SendString(int fd, const std::string& s) { return SendAll(fd,s.data(),s.size()); }
void Reply(int fd, int status, const char* reason, const std::string& more = "") {
  std::ostringstream header;
  header << "HTTP/1.1 " << status << " " << reason << "\r\n"
         << "Connection: close\r\nCache-Control: no-store\r\n"
         << "Accept-Ranges: bytes\r\nContent-Length: 0\r\n" << more << "\r\n";
  SendString(fd,header.str());
}
bool ParseUnsigned(const std::string& s,uint64_t& out) {
  if(s.empty() || s.size()>19) return false;
  uint64_t v=0;
  for(char c:s) {
    if(c<'0'||c>'9'||v>(UINT64_MAX-uint64_t(c-'0'))/10) return false;
    v=v*10+uint64_t(c-'0');
  }
  out=v;return true;
}
bool ReadRequest(int fd, std::string& request) {
  char buffer[512];
  while(request.size()<kMaxRequest) {
    const ssize_t n=recv(fd,buffer,sizeof(buffer),0);
    if(n<0&&errno==EINTR)continue;
    if(n<=0)return false;
    request.append(buffer,static_cast<size_t>(n));
    if(request.find("\r\n\r\n")!=std::string::npos)return true;
  }
  return false;
}
// A single-byte HTTP Range is standard. No multipart, suffixless invalid
// offsets, ambiguous multiple ranges, or user-provided filesystem paths.
bool ParseRange(const std::string& header, uint64_t fileSize,
                uint64_t& start, uint64_t& end, bool& requested) {
  start=0;end=fileSize-1;requested=false;
  std::istringstream lines(header);
  std::string line;
  std::getline(lines,line);
  while(std::getline(lines,line)) {
    if(line.size()>=6) {
      std::string key=line.substr(0,6);
      for(char& c:key) if(c>='A'&&c<='Z')c=char(c-'A'+'a');
      if(key=="range:") {
        if(requested)return false;
        requested=true;
        std::string value=line.substr(6);
        auto first=value.find_first_not_of(" \t");
        if(first==std::string::npos)return false;
        value=value.substr(first);
        if(value.rfind("bytes=",0)!=0||value.find(',')!=std::string::npos)return false;
        value=value.substr(6);
        const auto dash=value.find('-');
        if(dash==std::string::npos)return false;
        if(dash==0) {
          // Standard HTTP suffix range: bytes=-N means final N bytes.
          uint64_t suffix=0;
          std::string n=value.substr(1);
          while(!n.empty() && (n.back()=='\r'||n.back()==' '))n.pop_back();
          if(!ParseUnsigned(n,suffix)||suffix==0)return false;
          start=fileSize-std::min(suffix,fileSize);
          end=fileSize-1;
          continue;
        }
        if(!ParseUnsigned(value.substr(0,dash),start)||start>=fileSize)return false;
        std::string tail=value.substr(dash+1);
        while(!tail.empty() && (tail.back()=='\r'||tail.back()==' '))tail.pop_back();
        if(!tail.empty() && !ParseUnsigned(tail,end))return false;
        if(end<start)return false;
        end=std::min(end,fileSize-1);
      }
    }
  }
  return true;
}
void Demand(const std::shared_ptr<Session>& s,uint64_t offset) {
  s->demand.store(offset);
  s->generation.fetch_add(1);
  DemandStreamingRange(offset, 2 * 1024 * 1024, 0);
}
void Serve(int fd,std::shared_ptr<Session> s) {
  struct SocketCleanup {
    int fd;std::shared_ptr<Session> s;
    ~SocketCleanup() {
      std::lock_guard<std::mutex> lock(s->socketsLock);
      auto& fds=s->sockets;
      fds.erase(std::remove(fds.begin(),fds.end(),fd),fds.end());
      shutdown(fd,SHUT_RDWR);close(fd);
    }
  } cleanup{fd,s};
  timeval timeout{};timeout.tv_sec=4;
  setsockopt(fd,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout));
  timeout.tv_sec=12;
  setsockopt(fd,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout));
  std::string request;
  if(!ReadRequest(fd,request)) { ++s->failures;return; }
  const auto lineEnd=request.find("\r\n");
  if(lineEnd==std::string::npos) { Reply(fd,400,"Bad Request");return; }
  const std::string first=request.substr(0,lineEnd);
  const bool get=first.rfind("GET ",0)==0;
  const bool head=first.rfind("HEAD ",0)==0;
  if(!get&&!head) { Reply(fd,405,"Method Not Allowed");return; }
  const auto pathBegin=first.find(' ');
  const auto pathEnd=first.find(' ',pathBegin+1);
  if(pathEnd==std::string::npos || first.substr(pathBegin+1,pathEnd-pathBegin-1)
      !="/"+s->token+"/video."+s->mediaType.extension) { Reply(fd,404,"Not Found");return; }
  uint64_t start=0,end=0;bool range=false;
  if(!ParseRange(request,s->size,start,end,range)) {
    Reply(fd,416,"Range Not Satisfiable", "Content-Range: bytes */"+std::to_string(s->size)+"\r\n");
    return;
  }
  ++s->requests;
  Demand(s,start);
  // Do not commit a 200/206 response until the very first byte is verified.
  // This stops absent bytes being advertised as a successful complete file.
  const auto deadline=std::chrono::steady_clock::now()+kWaitLimit;
  std::vector<uint8_t> bytes;
  bool ready=false;
  if(get) {
    while(!s->closing.load() && std::chrono::steady_clock::now()<deadline) {
      if(ReadStreamingVerified(start,
           static_cast<uint32_t>(std::min<uint64_t>(kChunk,end-start+1)),bytes) && !bytes.empty()) {
        ready=true;break;
      }
      ++s->waiting;
      Demand(s,start);
      std::this_thread::sleep_for(std::chrono::milliseconds(150));
    }
    if(!ready) { ++s->failures;Reply(fd,503,"Verified Data Unavailable","Retry-After: 1\r\n");return; }
  }
  const uint64_t length=end-start+1;
  std::ostringstream header;
  header << "HTTP/1.1 " << (range?"206 Partial Content":"200 OK") << "\r\n"
         << "Connection: close\r\nAccept-Ranges: bytes\r\n"
         << "Cache-Control: no-store\r\nContent-Type: " << s->mediaType.contentType << "\r\n"
         << "Content-Length: " << length << "\r\n";
  if(range)header<<"Content-Range: bytes "<<start<<"-"<<end<<"/"<<s->size<<"\r\n";
  header<<"\r\n";
  if(!SendString(fd,header.str())||head)return;
  uint64_t offset=start;
  while(!s->closing.load()&&offset<=end) {
    const uint32_t count=static_cast<uint32_t>(std::min<uint64_t>(kChunk,end-offset+1));
    if(offset!=start || bytes.size()!=count) {
      bytes.clear();
      const auto until=std::chrono::steady_clock::now()+kWaitLimit;
      while(!s->closing.load()&&std::chrono::steady_clock::now()<until) {
        if(ReadStreamingVerified(offset,count,bytes)&&bytes.size()==count)break;
        ++s->waiting;Demand(s,offset);
        std::this_thread::sleep_for(std::chrono::milliseconds(150));
      }
      if(bytes.size()!=count) {++s->failures;return;} // Truncated connection is a read failure, NEVER EOF.
    }
    if(!SendAll(fd,bytes.data(),bytes.size()))return;
    s->served.fetch_add(bytes.size());
    offset+=bytes.size();
    bytes.clear();
  }
}
void AcceptLoop(std::shared_ptr<Session> s) {
  while(!s->closing.load()) {
    sockaddr_in addr{};socklen_t length=sizeof(addr);
    int client=accept(s->listener,reinterpret_cast<sockaddr*>(&addr),&length);
    if(client<0) {
      if(errno==EINTR)continue;
      if(s->closing.load())break;
      std::this_thread::sleep_for(std::chrono::milliseconds(50));continue;
    }
    if(ntohl(addr.sin_addr.s_addr)!=INADDR_LOOPBACK) {close(client);continue;}
    {
      std::lock_guard<std::mutex> lock(s->socketsLock);
      if(s->closing.load()||s->sockets.size()>=8) {close(client);continue;}
      s->sockets.push_back(client);
    }
    try {std::thread(Serve,client,s).detach();}
    catch(...) {std::lock_guard<std::mutex> lock(s->socketsLock);
      s->sockets.erase(std::remove(s->sockets.begin(),s->sockets.end(),client),s->sockets.end());close(client);}
  }
}
std::string Token() {
  // std::random_device uses platform OS entropy. Fail closed rather than using
  // a fixed token if entropy is not available.
  std::random_device entropy;
  const char* digits="0123456789abcdef";
  std::string value;
  for(int i=0;i<6;++i) {
    const uint32_t v=entropy();
    for(int n=0;n<8;++n)value+=digits[(v>>(n*4))&15];
  }
  return value;
}
}
void StopMpvRangeServer() {
  std::shared_ptr<Session> old;
  {std::lock_guard<std::mutex> lock(globalLock);old.swap(current);}
  if(!old)return;
  old->closing.store(true);
  if(old->listener>=0) {shutdown(old->listener,SHUT_RDWR);close(old->listener);old->listener=-1;}
  {std::lock_guard<std::mutex> lock(old->socketsLock);
    for(int fd:old->sockets)shutdown(fd,SHUT_RDWR);
  }
  if(old->acceptThread.joinable())old->acceptThread.join();
}
MpvRangeServerInfo StartMpvRangeServer(const std::string& hash,int fileIndex) {
  StopMpvRangeServer();
  MpvRangeServerInfo result;
  if(hash.size()!=40||fileIndex<0||fileIndex>100000) {result.status="Invalid torrent identity";return result;}
  auto s=std::make_shared<Session>();
  s->hash=hash;s->fileIndex=fileIndex;s->size=StreamingSelectedSize(hash,fileIndex);
  s->mediaType=TorrentMediaTypeFromName(StreamingSelectedName(hash,fileIndex));
  if(s->size==0) {result.status="Authenticated selected file unavailable";return result;}
  try {s->token=Token();}catch(...) {result.status="Unable to generate loopback session token";return result;}
  const int sock=socket(AF_INET,SOCK_STREAM,0);
  if(sock<0) {result.status="Loopback socket unavailable";return result;}
  s->listener=sock;
  sockaddr_in local{};local.sin_family=AF_INET;local.sin_addr.s_addr=htonl(INADDR_LOOPBACK);local.sin_port=0;
  if(bind(sock,reinterpret_cast<const sockaddr*>(&local),sizeof(local))!=0||listen(sock,8)!=0) {
    close(sock);s->listener=-1;result.status="Could not bind local range port";return result;
  }
  socklen_t localLen=sizeof(local);
  if(getsockname(sock,reinterpret_cast<sockaddr*>(&local),&localLen)!=0) {
    close(sock);s->listener=-1;result.status="Could not inspect loopback port";return result;
  }
  try {s->acceptThread=std::thread(AcceptLoop,s);} catch(...) {
    close(sock);s->listener=-1;result.status="Could not start local range listener";return result;
  }
  {std::lock_guard<std::mutex> lock(globalLock);current=s;}
  result.ready=true;
  result.url="http://127.0.0.1:"+std::to_string(ntohs(local.sin_port))+"/"+s->token+"/video."+s->mediaType.extension;
  result.status="Persistent libtorrent verified Range bridge ready";
  return result;
}
MpvRangeDemand CurrentMpvRangeDemand() {
  std::shared_ptr<Session> s;
  {std::lock_guard<std::mutex> lock(globalLock);s=current;}
  MpvRangeDemand result;
  if(!s)return result;
  result.active=!s->closing.load();result.offset=s->demand.load();
  result.generation=s->generation.load();result.requests=s->requests.load();
  result.servedBytes=s->served.load();result.waiting=s->waiting.load();result.failures=s->failures.load();
  return result;
}
}
