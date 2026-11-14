#pragma once
#include <string>

namespace pm_live_tv {
// Native UDP/Siano DVB-T405 receiver. All calls are fast and nonblocking;
// scan, receive, and localhost MPEG-TS streaming run on native threads.
bool Connect(const std::string& host, int control_port, int receive_port);
void Disconnect();
int ControlSocketFd(); // Owned by native receiver; never close this fd from Kotlin.
bool Probe();
bool Tune(int frequency_hz, int bandwidth_mhz);
bool Scan(const std::string& profile);
void CancelScan();
std::string StatusJson();
std::string ChannelsJson();
int RestoreChannels(const std::string& data); // Validated cached DVB identities/locations.
std::string Play(const std::string& channel_key);
void StopPlay();
bool RecoverStream(); // Guarded soft PID restore, then same-multiplex retune on sustained stall.
} // namespace pm_live_tv
