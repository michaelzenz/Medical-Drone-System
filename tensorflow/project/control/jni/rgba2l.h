#ifndef TENSORFLOW_EXAMPLES_ANDROID_JNI_RGBA2LUMINANCE_H_
#define TENSORFLOW_EXAMPLES_ANDROID_JNI_RGBA2LUMINANCE_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// The same as above, but downsamples each dimension to half size.
void ConvertRGBAToLUMINANCE(const uint8_t* const input,
                                       uint8_t* const output, int width,
                                       int height) ;

#ifdef __cplusplus
}
#endif

#endif  // TENSORFLOW_EXAMPLES_ANDROID_JNI_RGB2ARGB_H_