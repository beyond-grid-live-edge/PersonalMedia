#pragma once
// C.19.0.3: privacy-conscious native networking preflight and alert operation
// extraction. The result includes ONLY syscall categories and errno values,
// never IPs, private interface names, user paths, or tracker URLs.
#include <cerrno>
#include <ifaddrs.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#include <string>
#include <initializer_list>
#if __has_include(<linux/netlink.h>)
#include <linux/netlink.h>
#define PM_HAS_NETLINK_ROUTE_PROBE 1
#else
#define PM_HAS_NETLINK_ROUTE_PROBE 0
#endif
namespace pm_torrent {
// Diagnostic-only, no listening socket is kept open. These probes distinguish
// normal app Internet access from native interface/socket sandbox restrictions.
// Never reveal local IP addresses or interface names in user-facing output.
inline std::string NativeNetworkPreflight() {
  auto probeBind=[](int type)->std::string {
    const int fd=::socket(AF_INET,type,0);
    if(fd<0)return std::string("socket-error-")+std::to_string(errno);
    sockaddr_in addr{};
    addr.sin_family=AF_INET;
    addr.sin_addr.s_addr=htonl(INADDR_ANY);
    addr.sin_port=0; // OS-selected unprivileged port
    const int rc=::bind(fd,reinterpret_cast<sockaddr*>(&addr),sizeof(addr));
    const int error=errno;
    ::close(fd);
    return rc==0 ? "ok" : "bind-error-"+std::to_string(error);
  };
  const std::string tcp=probeBind(SOCK_STREAM);
  const std::string udp=probeBind(SOCK_DGRAM);
  // A simple bind() probe does not exercise TCP listen() or the socket
  // options needed by a BitTorrent listener. These are still diagnostic
  // only and never exchange network traffic.
  auto probeListen=[]()->std::string {
    const int fd=::socket(AF_INET,SOCK_STREAM,0);
    if(fd<0)return std::string("socket-error-")+std::to_string(errno);
    int reuse=1;
    if(::setsockopt(fd,SOL_SOCKET,SO_REUSEADDR,&reuse,sizeof(reuse))!=0) {
      const int err=errno; ::close(fd);
      return "reuse-error-"+std::to_string(err);
    }
    sockaddr_in addr{};
    addr.sin_family=AF_INET;
    addr.sin_addr.s_addr=htonl(INADDR_ANY);
    addr.sin_port=0;
    if(::bind(fd,reinterpret_cast<sockaddr*>(&addr),sizeof(addr))!=0) {
      const int err=errno; ::close(fd);
      return "bind-error-"+std::to_string(err);
    }
    const int rc=::listen(fd,1);
    const int err=errno;
    ::close(fd);
    return rc==0 ? "ok" : "listen-error-"+std::to_string(err);
  };
  auto probeIPv6=[](int type)->std::string {
    const int fd=::socket(AF_INET6,type,0);
    if(fd<0)return std::string("socket-error-")+std::to_string(errno);
    sockaddr_in6 addr{};
    addr.sin6_family=AF_INET6;
    addr.sin6_addr=in6addr_any;
    addr.sin6_port=0;
    const int rc=::bind(fd,reinterpret_cast<sockaddr*>(&addr),sizeof(addr));
    const int err=errno;
    ::close(fd);
    return rc==0 ? "ok" : "bind-error-"+std::to_string(err);
  };
  // Diagnose the syscall family that libtorrent normally selects on Linux.
  // Failure here must not be treated as a failure of ordinary networking.
  auto probeNetlink=[]()->std::string {
#if PM_HAS_NETLINK_ROUTE_PROBE
    const int fd=::socket(AF_NETLINK,SOCK_RAW,NETLINK_ROUTE);
    if(fd<0) return "socket-error-"+std::to_string(errno);
    sockaddr_nl addr{};
    addr.nl_family=AF_NETLINK;
    const int rc=::bind(fd,reinterpret_cast<sockaddr*>(&addr),sizeof(addr));
    const int err=errno;
    ::close(fd);
    return rc==0 ? "ok" : "bind-error-"+std::to_string(err);
#else
    return "unavailable";
#endif
  };
  const std::string netlink=probeNetlink();
  const std::string tcpListen=probeListen();
  const std::string tcp6=probeIPv6(SOCK_STREAM);
  const std::string udp6=probeIPv6(SOCK_DGRAM);
  ifaddrs* interfaces=nullptr;
  const int rc=::getifaddrs(&interfaces);
  const int err=errno;
  if(interfaces!=nullptr)::freeifaddrs(interfaces);
  return "native TCP="+tcp+", UDP="+udp+", interfaces="+
    (rc==0 ? "ok" : "error-"+std::to_string(err))+
    ", TCP-listen="+tcpListen+", TCP6="+tcp6+", UDP6="+udp6+", netlink-route="+netlink;
}
// libtorrent's session_error_alert wraps the native operation context in
// message(), but that may include paths/URLs. Allow-list only operation words.
inline std::string SafeSessionOperation(const std::string& message) {
  for(const char* op : {"getifaddrs", "bind", "listen", "connect", "socket", "resolve", "setsockopt"}) {
    if(message.find(op)!=std::string::npos)return op;
  }
  return "unknown operation";
}
} // namespace pm_torrent
