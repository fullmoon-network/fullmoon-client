use super::{sha1_file, PackSource, Refresh};
use crate::{
    atomic::TempFile,
    error::{Error, Result},
};
use sha1::{Digest, Sha1};
use std::path::Path;

const GUI_PACK: &[u8] = include_bytes!(concat!(
    env!("CARGO_MANIFEST_DIR"),
    "/../../i3/mod/src/main/resources/fullmoon/gui-pack.zip"
));

pub(super) async fn refresh(pack: &PackSource, path: &Path) -> Result<Refresh> {
    if pack.id != "fullmoon-gui" || pack.file != "fullmoon-gui.zip" {
        return Err(Error::Invalid(format!(
            "no bundled resource pack exists for {}",
            pack.id
        )));
    }
    let want = hex::encode(Sha1::digest(GUI_PACK));
    if sha1_file(path).await.as_deref() == Some(want.as_str()) {
        return Ok(Refresh::UpToDate);
    }
    let directory = path
        .parent()
        .ok_or_else(|| Error::Invalid("the pack has no parent directory".into()))?;
    tokio::fs::create_dir_all(directory).await?;
    let mut temporary = TempFile::create(directory, &pack.file).await?;
    if let Err(error) = temporary.write_all(GUI_PACK).await {
        return Err(temporary.discard(error).await);
    }
    temporary.commit(path).await?;
    Ok(Refresh::Updated)
}
