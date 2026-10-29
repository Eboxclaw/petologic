set(YARA_DIR "${CMAKE_CURRENT_SOURCE_DIR}/vendor/yara/libyara")
if(NOT EXISTS "${YARA_DIR}/compiler.c")
 message(FATAL_ERROR "Run android/scripts/bootstrap-security.sh first")
endif()
file(GLOB YARA_CORE CONFIGURE_DEPENDS "${YARA_DIR}/*.c")
add_library(paladino_security SHARED security_yara.cpp ${YARA_CORE}
 "${YARA_DIR}/proc/none.c" "${YARA_DIR}/tlshc/tlsh.c" "${YARA_DIR}/tlshc/tlsh_impl.c" "${YARA_DIR}/tlshc/tlsh_util.c"
 "${YARA_DIR}/modules/tests/tests.c" "${YARA_DIR}/modules/elf/elf.c"
 "${YARA_DIR}/modules/math/math.c" "${YARA_DIR}/modules/time/time.c"
 "${YARA_DIR}/modules/pe/pe.c" "${YARA_DIR}/modules/pe/pe_utils.c"
 "${YARA_DIR}/modules/console/console.c" "${YARA_DIR}/modules/string/string.c"
 "${YARA_DIR}/modules/dex/dex.c")
target_include_directories(paladino_security PRIVATE "${YARA_DIR}/include" "${YARA_DIR}")
target_compile_definitions(paladino_security PRIVATE _GNU_SOURCE USE_NO_PROC HAVE_SCAN_PROC_IMPL=0
 BUCKETS_128=1 CHECKSUM_1B=1 HAVE_STDBOOL_H HAVE_STRLCPY HAVE_STRLCAT HAVE_MEMMEM DEX_MODULE)
target_compile_options(paladino_security PRIVATE -O2)
target_link_libraries(paladino_security PRIVATE log m)
target_link_options(paladino_security PRIVATE "-Wl,-z,max-page-size=16384")
