#!/system/bin/sh
# Chroot mode with unshare/nsenter support
set -e

ALPINE_DIR=$PREFIX/local/alpine

mkdir -p "$ALPINE_DIR"

# Extract Alpine if not already extracted
if [ -z "$(ls -A "$ALPINE_DIR" | grep -vE '^(root|tmp)$')" ]; then
    tar -xf "$PREFIX/files/alpine.tar.gz" -C "$ALPINE_DIR"
fi

# Ensure tmp directory exists with correct permissions
if [ ! -d "$PREFIX/local/alpine/tmp" ]; then
    mkdir -p "$PREFIX/local/alpine/tmp"
    chmod 1777 "$PREFIX/local/alpine/tmp"
fi

# Determine unshare mode: 0=OWN_NS, 1=FIRST_ONLY, 2=NO_UNSHARE
UNSHARE_MODE=${UNSHARE_MODE:-1}

# Flag file to track first session
FIRST_SESSION_FLAG="$PROOT_TMP_DIR/../first_session_pid"

# Check if we should unshare or nsenter
SHOULD_UNSHARE=0
NSENTER_PID=""

if [ "$UNSHARE_MODE" -eq 0 ]; then
    # OWN_NS: Always unshare for each session
    SHOULD_UNSHARE=1
elif [ "$UNSHARE_MODE" -eq 1 ]; then
    # FIRST_ONLY: Check if first session exists
    if [ -f "$FIRST_SESSION_FLAG" ]; then
        NSENTER_PID=$(cat "$FIRST_SESSION_FLAG")
        if [ -d "/proc/$NSENTER_PID" ]; then
            # First session exists, we should nsenter
            SHOULD_UNSHARE=0
        else
            # PID in flag file doesn't exist, create new first session
            SHOULD_UNSHARE=1
            rm -f "$FIRST_SESSION_FLAG"
        fi
    else
        # No first session, we should unshare
        SHOULD_UNSHARE=1
    fi
elif [ "$UNSHARE_MODE" -eq 2 ]; then
    # NO_UNSHARE: Never unshare
    SHOULD_UNSHARE=0
fi

# Bind mount system directories for chroot
mount_system() {
    # Mount critical Android paths
    for system_mnt in /apex /odm /product /system /system_ext /vendor; do
        if [ -e "$system_mnt" ]; then
            system_mnt=$(realpath "$system_mnt")
            mount --bind "$system_mnt" "$ALPINE_DIR$system_mnt" 2>/dev/null || true
        fi
    done
    
    # Mount device nodes and pseudo filesystems
    mount --bind /dev "$ALPINE_DIR/dev" 2>/dev/null || true
    mount --bind /proc "$ALPINE_DIR/proc" 2>/dev/null || true
    mount --bind /sys "$ALPINE_DIR/sys" 2>/dev/null || true
    
    # Mount storage
    mkdir -p "$ALPINE_DIR/sdcard" "$ALPINE_DIR/storage"
    mount --bind /sdcard "$ALPINE_DIR/sdcard" 2>/dev/null || true
    mount --bind /storage "$ALPINE_DIR/storage" 2>/dev/null || true
}

if [ "$SHOULD_UNSHARE" -eq 1 ]; then
    # Unshare mode: Create new namespaces and run init
    # Use -C -i -m -n -p -u flags
    # -C: unshare cgroup namespace
    # -i: unshare IPC namespace  
    # -m: unshare mount namespace
    # -n: unshare network namespace
    # -p: unshare PID namespace
    # -u: unshare UTS namespace
    
    # Create a script to run in the namespace
    cat > "$PROOT_TMP_DIR/chroot-exec.sh" << 'EOFSCRIPT'
#!/system/bin/sh
set -e

# $1 is ALPINE_DIR, shift to get remaining args
ALPINE_DIR="$1"
shift

# Save our PID for FIRST_ONLY mode
if [ "$UNSHARE_MODE" -eq 1 ] && [ -n "$FIRST_SESSION_FLAG" ]; then
    echo $$ > "$FIRST_SESSION_FLAG"
fi

# Mount system directories
for system_mnt in /apex /odm /product /system /system_ext /vendor; do
    if [ -e "$system_mnt" ]; then
        mkdir -p "$ALPINE_DIR$system_mnt" 2>/dev/null || true
        mount --bind "$system_mnt" "$ALPINE_DIR$system_mnt" 2>/dev/null || true
    fi
done

# Mount proc, sys, dev
mkdir -p "$ALPINE_DIR/proc" "$ALPINE_DIR/sys" "$ALPINE_DIR/dev" 2>/dev/null || true
mount -t proc proc "$ALPINE_DIR/proc" 2>/dev/null || true
mount -t sysfs sys "$ALPINE_DIR/sys" 2>/dev/null || true
mount --bind /dev "$ALPINE_DIR/dev" 2>/dev/null || true

# Mount storage
mkdir -p "$ALPINE_DIR/sdcard" "$ALPINE_DIR/storage" 2>/dev/null || true
mount --bind /sdcard "$ALPINE_DIR/sdcard" 2>/dev/null || true
mount --bind /storage "$ALPINE_DIR/storage" 2>/dev/null || true

# Chroot and exec init as PID 1
exec chroot "$ALPINE_DIR" /sbin/init "$@"
EOFSCRIPT
    
    chmod +x "$PROOT_TMP_DIR/chroot-exec.sh"
    
    # Export variables for the script
    export UNSHARE_MODE
    export FIRST_SESSION_FLAG
    
    # Execute with unshare, using exec to preserve PID
    exec unshare -C -i -m -n -p -u -f --mount-proc "$PROOT_TMP_DIR/chroot-exec.sh" "$ALPINE_DIR" "$@"
    
else
    # Nsenter or no-unshare mode: Join existing namespace or run without namespace isolation
    
    if [ -n "$NSENTER_PID" ] && [ -d "/proc/$NSENTER_PID" ]; then
        # Nsenter mode: Join the existing namespace and launch shell
        # The init is already running as PID 1 in that namespace
        # Launch shell and it will be reparented to init
        
        # Use nsenter with same flags: -C -i -m -n -p -u
        exec nsenter -C -i -m -n -p -u -t "$NSENTER_PID" chroot "$ALPINE_DIR" /bin/sh "$@"
    else
        # No unshare mode: Simple chroot without namespace isolation
        # Mount system directories first
        mount_system
        
        # Chroot and run shell
        exec chroot "$ALPINE_DIR" /bin/sh "$@"
    fi
fi
