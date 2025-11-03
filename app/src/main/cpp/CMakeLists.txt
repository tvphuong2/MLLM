cmake_minimum_required(VERSION 3.22.1)

project(rag_agent)

add_library(rag-agent SHARED
    native_bridge.cpp
)

target_compile_features(rag-agent PRIVATE cxx_std_17)

target_compile_options(rag-agent PRIVATE -O3 -fvisibility=hidden)

find_library(log-lib log)

if (log-lib)
    target_link_libraries(rag-agent PRIVATE ${log-lib})
endif()
