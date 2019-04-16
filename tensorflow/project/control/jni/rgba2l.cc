//
// Created by michaelz on 2/22/19.
//


// This is a function to convert RGB to ARGB

#include "tensorflow/project/control/jni/rgba2l.h"

// The same as above, but downsamples each dimension to half size.
void ConvertRGBAToLUMINANCE(const uint8_t* const input,
                                       uint8_t* const output, int width,
                                       int height) {

  const uint8_t* in = input;
  uint8_t* out = output;
  int frameSize = width * height;

    for(int i=0;i<frameSize;i++){
      out[i] = (uint8_t)(in[i*4]*0.299 + in[i*4+1]*0.587 + in [i*4+2]*0.114);
    }

}

