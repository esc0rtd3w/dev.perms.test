cmake_minimum_required(VERSION 3.10.2)

project(perms_test_capstone_bridge LANGUAGES C CXX)

# The app keeps the Capstone bridge isolated from the rest of the memory-overlay code.
# Capstone is linked from the prebuilt archives supplied with the Android-Disassembler
# reference project so the Java overlay can call one small JNI surface without adding
# Kotlin bindings, JNA, or disassembler-specific UI dependencies.
add_library(capstone STATIC IMPORTED)
set_target_properties(capstone PROPERTIES
        IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/capstone-libs/${ANDROID_ABI}/libcapstone.a")

add_library(perms_test_capstone SHARED
        capstone_bridge.cpp)

target_include_directories(perms_test_capstone PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/capstone/include")

target_compile_features(perms_test_capstone PRIVATE cxx_std_17)

target_link_libraries(perms_test_capstone
        capstone
        log
        dl)
