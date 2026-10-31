if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "/opt/gradle/.gradle/caches/9.3.1/transforms/f50e03369f17b75a9d57023344b262df/transformed/oboe-1.8.1/prefab/modules/oboe/libs/android.arm64-v8a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "/opt/gradle/.gradle/caches/9.3.1/transforms/f50e03369f17b75a9d57023344b262df/transformed/oboe-1.8.1/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

