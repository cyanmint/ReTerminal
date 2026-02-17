ALPINE_DIR=$PREFIX/local/alpine

mkdir -p $ALPINE_DIR

if [ -z "$(ls -A "$ALPINE_DIR" | grep -vE '^(root|tmp)$')" ]; then
    tar -xf "$PREFIX/files/alpine.tar.gz" -C "$ALPINE_DIR"
fi

if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
 mkdir -p "$PREFIX/local/alpine/tmp"
 chmod 1777 "$PREFIX/local/alpine/tmp"
fi

# Create necessary directories for bind mounts
mkdir -p "$ALPINE_DIR/sdcard"
mkdir -p "$ALPINE_DIR/storage"
mkdir -p "$ALPINE_DIR/dev"
mkdir -p "$ALPINE_DIR/proc"
mkdir -p "$ALPINE_DIR/sys"
mkdir -p "$ALPINE_DIR$PREFIX"

# Use su to run commands with root privileges
# unshare creates new namespaces (mount, PID, UTS, IPC)
# chroot changes the root directory
su -c "
    # Create new namespaces: mount, PID, UTS, IPC
    unshare -m -p -u -i -f sh -c '
        # Mount proc for the new PID namespace
        mount -t proc proc $ALPINE_DIR/proc
        
        # Bind mount necessary directories
        mount --bind /sdcard $ALPINE_DIR/sdcard
        mount --bind /storage $ALPINE_DIR/storage
        mount --bind /dev $ALPINE_DIR/dev
        mount --bind /sys $ALPINE_DIR/sys
        mount --bind $PREFIX $ALPINE_DIR$PREFIX
        mount --bind $PREFIX/local/stat $ALPINE_DIR/proc/stat
        mount --bind $PREFIX/local/vmstat $ALPINE_DIR/proc/vmstat
        
        # Bind mount /dev/shm
        mount --bind $PREFIX/local/alpine/tmp $ALPINE_DIR/dev/shm
        
        # Bind mount system directories
        for system_mnt in /apex /odm /product /system /system_ext /vendor; do
            if [ -e \"\$system_mnt\" ]; then
                mkdir -p \"$ALPINE_DIR\$system_mnt\"
                mount --bind \"\$system_mnt\" \"$ALPINE_DIR\$system_mnt\"
            fi
        done
        
        # Create linkerconfig directories if needed
        if [ -e /linkerconfig/ld.config.txt ]; then
            mkdir -p $ALPINE_DIR/linkerconfig
            mount --bind /linkerconfig/ld.config.txt $ALPINE_DIR/linkerconfig/ld.config.txt
        fi
        
        if [ -e /linkerconfig/com.android.art/ld.config.txt ]; then
            mkdir -p $ALPINE_DIR/linkerconfig/com.android.art
            mount --bind /linkerconfig/com.android.art/ld.config.txt $ALPINE_DIR/linkerconfig/com.android.art/ld.config.txt
        fi
        
        # Bind mount property contexts
        if [ -e /plat_property_contexts ]; then
            touch $ALPINE_DIR/plat_property_contexts
            mount --bind /plat_property_contexts $ALPINE_DIR/plat_property_contexts
        fi
        
        if [ -e /property_contexts ]; then
            touch $ALPINE_DIR/property_contexts
            mount --bind /property_contexts $ALPINE_DIR/property_contexts
        fi
        
        # Bind mount /dev/random
        mount --bind /dev/urandom $ALPINE_DIR/dev/random
        
        # Set up /dev/fd, stdin, stdout, stderr
        if [ -e /proc/self/fd ]; then
            mount --bind /proc/self/fd $ALPINE_DIR/dev/fd
        fi
        
        if [ -e /proc/self/fd/0 ]; then
            mount --bind /proc/self/fd/0 $ALPINE_DIR/dev/stdin
        fi
        
        if [ -e /proc/self/fd/1 ]; then
            mount --bind /proc/self/fd/1 $ALPINE_DIR/dev/stdout
        fi
        
        if [ -e /proc/self/fd/2 ]; then
            mount --bind /proc/self/fd/2 $ALPINE_DIR/dev/stderr
        fi
        
        # Change root and execute init script
        chroot $ALPINE_DIR /bin/sh $PREFIX/local/bin/init \"\$@\"
    '
"
