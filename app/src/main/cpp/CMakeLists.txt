cmake_minimum_required(VERSION 3.22.1)
project("meshllm" C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
if (NOT EXISTS ${LLAMA_SRC}/CMakeLists.txt)
    message(FATAL_ERROR "llama.cpp source missing. Run tools/fetch_llama_cpp.sh (Gradle runs it automatically).")
endif()

# Same configuration as llama.cpp's official Android example: every CPU variant is built and the best one
# for the phone's chip is picked at runtime, so older CPUs get a slower path instead of a crash.
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_SRC} build-llama)

add_library(meshllm SHARED meshllm.cpp)
target_include_directories(meshllm PRIVATE ${LLAMA_SRC}/include ${LLAMA_SRC}/ggml/include)
if (ANDROID)
    target_link_libraries(meshllm llama ggml android log)
else()
    # Desktop build for testing the JNI bridge on a PC (tools/jni_test).
    find_package(JNI REQUIRED)
    target_include_directories(meshllm PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(meshllm llama ggml)
endif()
