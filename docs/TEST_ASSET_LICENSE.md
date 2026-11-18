# Test media

app/src/main/assets/test-pattern.mp4 is an original synthetic test pattern and sine-wave tone generated for this project with FFmpeg lavfi testsrc2/sine and encoded with H.264/AAC. It contains no third-party footage or music.

The generated test video and docs/test-subtitles-fa.srt are dedicated to the public domain under CC0 1.0:
https://creativecommons.org/publicdomain/zero/1.0/

Generation command:
```text
ffmpeg -f lavfi -i testsrc2=size=640x360:rate=30 -f lavfi -i sine=frequency=440:sample_rate=48000 -t 8 -c:v libx264 -pix_fmt yuv420p -preset fast -crf 26 -c:a aac -b:a 64k -movflags +faststart test-pattern.mp4
```

The optional configurable online sample is not bundled. Its availability is outside this project.
