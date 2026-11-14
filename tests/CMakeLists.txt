cmake_minimum_required(VERSION 3.16)
project(pm_native_host_smoke LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
find_package(Threads REQUIRED)
add_executable(native_smoke native_smoke.cpp
    ../app/src/main/cpp/portable/native_live_tv.cpp
    ../app/src/main/cpp/portable/streaming_torrent_session.cpp
    ../app/src/main/cpp/portable/mpv_range_server.cpp)
target_link_libraries(native_smoke PRIVATE Threads::Threads)
enable_testing()
add_test(NAME native_smoke COMMAND native_smoke)
add_executable(str5_torrent_selection_test str5_torrent_selection_test.cpp
    ../app/src/main/cpp/portable/streaming_torrent_session.cpp)
target_link_libraries(str5_torrent_selection_test PRIVATE Threads::Threads)
add_test(NAME str5_torrent_selection_test COMMAND str5_torrent_selection_test)
add_executable(str5e_torrent_cancel_test str5e_torrent_cancel_test.cpp
    ../app/src/main/cpp/portable/streaming_torrent_session.cpp)
target_link_libraries(str5e_torrent_cancel_test PRIVATE Threads::Threads)
add_test(NAME str5e_torrent_cancel_test COMMAND str5e_torrent_cancel_test)
add_executable(str5g_torrent_restart_test str5g_torrent_restart_test.cpp
    ../app/src/main/cpp/portable/streaming_torrent_session.cpp)
target_link_libraries(str5g_torrent_restart_test PRIVATE Threads::Threads)
add_test(NAME str5g_torrent_restart_test COMMAND str5g_torrent_restart_test)
foreach(test IN ITEMS c2001_udp_tuner_stream_test c2004_t405_battery_probe_test c2003_t405_receiver_lifecycle_test c2001_native_t405_host_test c2005_t405_socket_telemetry_test c2006_t405_targeted_pmt_test c2007_t405_lineup_schedule_test c2008_t405_guardian_recovery_test c2009_t405_eit_schedule_test c2010_t405_ltv4_acceptance_test)
    add_executable(${test} ${test}.cpp)
    target_link_libraries(${test} PRIVATE Threads::Threads)
    add_test(NAME ${test} COMMAND ${test})
endforeach()
