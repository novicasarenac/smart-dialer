# SMART-DIALER 

Android application for phone number recognition from taken photo or written number on panel, and calling that number from mobile phone.

# Usage

Clone project with:
```
git clone https://github.com/novicasarenac/smart-dialer.git
```
Import project in Android Studio and run it.

# About

Image processing: 
RGB -> Grayscale -> Threshold 

Morphological operations used for image processing:
[Dilatation](https://en.wikipedia.org/wiki/Dilation_(morphology))

Classifier used for number recognition:
[K-nearest neighbors](https://en.wikipedia.org/wiki/K-nearest_neighbors_algorithm)

# External links

Python code for image processing:
https://github.com/markovjestica/smart-dialer-python-engine
