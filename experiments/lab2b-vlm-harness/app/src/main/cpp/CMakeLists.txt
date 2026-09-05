cmake_minimum_required(VERSION 3.22.1)
project(lab2b_mnn)
set(CMAKE_CXX_STANDARD 17)
set(DEPS "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.deps")
add_library(lab2b_mnn SHARED mnn_bridge.cpp)
target_include_directories(lab2b_mnn PRIVATE
    "${DEPS}/headers/include"
    "${DEPS}/headers/transformers/llm/engine/include")
foreach(NAME llm MNN MNN_Express MNN_CL MNN_Vulkan MNNOpenCV MNNAudio)
    add_library(${NAME} SHARED IMPORTED)
    set_target_properties(${NAME} PROPERTIES IMPORTED_LOCATION "${DEPS}/jniLibs/${ANDROID_ABI}/lib${NAME}.so")
endforeach()
target_link_libraries(lab2b_mnn llm MNN MNN_Express log)
target_link_options(lab2b_mnn PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
