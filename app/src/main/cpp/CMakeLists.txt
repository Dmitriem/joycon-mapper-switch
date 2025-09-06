cmake_minimum_required(VERSION 3.22.1)

project("joyconmapper")

# Добавляем библиотеку
add_library(
    joyconmapper
    SHARED
    joyconmapper.cpp
)

# Находим библиотеки
find_library(
    log-lib
    log
)

find_library(
    android-lib
    android
)

# Линкуем библиотеки
target_link_libraries(
    joyconmapper
    ${log-lib}
    ${android-lib}
)

# Устанавливаем стандарт C++
set_property(TARGET joyconmapper PROPERTY CXX_STANDARD 11)