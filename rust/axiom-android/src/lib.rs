//! JNI / native bridge surface for Android packaging.
//! Host builds compile this as cdylib; full JNI hooks land with NDK CI.

pub mod bridge;
pub mod lifecycle;

pub use bridge::handle_request;
