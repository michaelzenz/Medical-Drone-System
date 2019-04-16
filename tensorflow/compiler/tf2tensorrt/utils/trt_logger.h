/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

#ifndef TENSORFLOW_COMPILER_TF2TENSORRT_UTILS_TRT_LOGGER_H_
#define TENSORFLOW_COMPILER_TF2TENSORRT_UTILS_TRT_LOGGER_H_

#include "tensorflow/core/platform/types.h"

#if GOOGLE_CUDA
#if GOOGLE_TENSORRT
#include "tensorrt/include/NvInfer.h"

namespace tensorflow {
namespace tensorrt {

// Logger for GIE info/warning/errors
class Logger : public nvinfer1::ILogger {
 public:
  Logger(string name = "DefaultLogger") : name_(name) {}
  void log(nvinfer1::ILogger::Severity severity, const char* msg) override;

 private:
  string name_;
};

}  // namespace tensorrt
}  // namespace tensorflow

#endif  // GOOGLE_TENSORRT
#endif  // GOOGLE_CUDA

#endif  // TENSORFLOW_COMPILER_TF2TENSORRT_UTILS_TRT_LOGGER_H_
