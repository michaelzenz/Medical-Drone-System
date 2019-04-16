
// This is a function to convert RGB to ARGB

#include "tensorflow/project/control/jni/rgba2argb.h"

// The same as above, but downsamples each dimension to half size.
void ConvertRGBAToARGB8888(const uint8_t* const input,
                                       uint32_t* const output, int width,
                                       int height) {
            
  const uint8_t* in = input;
  uint32_t* out = output;
  int frameSize=width*height;

  for(int i=0;i<frameSize;i++){
    *out++=(in[i*4+3]<<24 | in[i*4]<<16 | in[i*4+1]<<8 | in[i*4+2]);
  }
  
}

