cmake_minimum_required(VERSION 3.22.1)
project(gabitama_tama_pty C)

# JNI function names and package are intentionally retained from upstream.
add_library(pty_bridge SHARED pty_bridge.c)
find_library(android_log log)
target_link_libraries(pty_bridge ${android_log})
