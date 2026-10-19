pub mod bridge;
pub mod lifecycle;

use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;

fn to_jstring(env: &mut JNIEnv, s: String) -> jstring {
    env.new_string(s)
        .map(|j| j.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_axiom_mobile_MainActivity_nativeIdentity(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    to_jstring(&mut env, bridge::identity())
}

#[no_mangle]
pub extern "system" fn Java_com_axiom_mobile_MainActivity_nativeInitWorkspace(
    mut env: JNIEnv,
    _class: JClass,
    path: JString,
) -> jstring {
    let p: String = env
        .get_string(&path)
        .map(|s| s.into())
        .unwrap_or_else(|_| "/data/data/com.axiom.mobile/files".into());
    to_jstring(&mut env, bridge::init_workspace(&p))
}

#[no_mangle]
pub extern "system" fn Java_com_axiom_mobile_MainActivity_nativeExecute(
    mut env: JNIEnv,
    _class: JClass,
    action_json: JString,
) -> jstring {
    let j: String = env
        .get_string(&action_json)
        .map(|s| s.into())
        .unwrap_or_else(|_| "{}".into());
    to_jstring(&mut env, bridge::handle_request(&j))
}
