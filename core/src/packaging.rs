use rand::RngCore;
use thiserror::Error;

pub const BLOCK_SIZE: usize = 262_144;
pub const FRAME_SIZE: usize = 262_100;
pub const CONTENT_CAPACITY: usize = FRAME_SIZE - 4;

#[derive(Debug, Error)]
pub enum PackagingError {
    #[error("content too large for a single Garland frame")]
    ContentTooLarge,
    #[error("frame length is invalid")]
    InvalidFrameLength,
    #[error("frame content length is invalid")]
    InvalidContentLength,
}

pub fn frame_content(content: &[u8]) -> Result<Vec<u8>, PackagingError> {
    if content.len() > CONTENT_CAPACITY {
        return Err(PackagingError::ContentTooLarge);
    }

    let mut frame = vec![0_u8; FRAME_SIZE];
    let length = (content.len() as u32).to_be_bytes();
    frame[..4].copy_from_slice(&length);
    frame[4..4 + content.len()].copy_from_slice(content);
    rand::rngs::OsRng.fill_bytes(&mut frame[4 + content.len()..]);
    Ok(frame)
}

pub fn unframe_content(frame: &[u8]) -> Result<Vec<u8>, PackagingError> {
    if frame.len() != FRAME_SIZE {
        return Err(PackagingError::InvalidFrameLength);
    }

    let content_length = u32::from_be_bytes(frame[..4].try_into().unwrap()) as usize;
    if content_length > CONTENT_CAPACITY {
        return Err(PackagingError::InvalidContentLength);
    }

    Ok(frame[4..4 + content_length].to_vec())
}

#[cfg(test)]
mod tests {
    use super::{frame_content, unframe_content, CONTENT_CAPACITY, FRAME_SIZE};

    #[test]
    fn round_trips_framed_content() {
        let content = b"hello garland";

        let frame = frame_content(content).expect("frame should succeed");

        assert_eq!(frame.len(), FRAME_SIZE);
        assert_eq!(
            unframe_content(&frame).expect("unframe should succeed"),
            content
        );
    }

    #[test]
    fn fills_padding_with_non_deterministic_bytes() {
        let content = b"short";

        let frame_a = frame_content(content).expect("first frame should succeed");
        let frame_b = frame_content(content).expect("second frame should succeed");

        assert_ne!(&frame_a[4 + content.len()..], &frame_b[4 + content.len()..]);
    }

    #[test]
    fn rejects_content_larger_than_capacity() {
        let content = vec![7_u8; CONTENT_CAPACITY + 1];

        assert!(frame_content(&content).is_err());
    }
}
