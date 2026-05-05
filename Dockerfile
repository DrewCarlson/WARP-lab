FROM ubuntu:noble

RUN sed -i -E 's|https?://(archive\|security)\.ubuntu\.com/ubuntu|http://us-east-1.ec2.archive.ubuntu.com/ubuntu|g' \
           /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends libcurl4 ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY build/bin/linuxX64/releaseExecutable/warplab-controller.kexe ./warplab-controller-x64
COPY build/bin/linuxArm64/releaseExecutable/warplab-controller.kexe ./warplab-controller-arm64

RUN if [ "$(uname -m)" = "x86_64" ]; then mv /app/warplab-controller-x64 /app/warplab-controller; else mv /app/warplab-controller-arm64 /app/warplab-controller; fi

RUN chmod u+x warplab-controller

ENTRYPOINT ["/app/warplab-controller"]
