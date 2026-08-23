FROM eclipse-temurin:17-jdk-jammy

# Set environment variables
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/build-tools/34.0.0

# Install required system tools
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    unzip \
    git \
    bash \
    file \
    && rm -rf /var/lib/apt/lists/*

# Download and install Android Command Line Tools
WORKDIR /opt/android-sdk
RUN mkdir -p cmdline-tools && \
    curl -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q cmdline-tools.zip -d cmdline-tools && \
    mv cmdline-tools/cmdline-tools cmdline-tools/latest && \
    rm cmdline-tools.zip

# Accept Android SDK licenses and install packages
RUN yes | sdkmanager --licenses && \
    sdkmanager --install \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0"

# Setup workspace
WORKDIR /app
COPY . /app

# Ensure Gradle wrapper is executable
RUN chmod +x gradlew

# Default command compiles debug APK and tests
CMD ["./gradlew", "assembleDebug"]
