// the launcher is the window; a console behind it is debug-only noise
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod auth;
mod catalog;
mod commands;
mod cosmetics;
mod download;
mod error;
mod fabric;
mod hud;
mod install;
mod java;
mod launch;
mod meta;
mod model;
mod modlock;
mod mods;
mod offline;
mod paths;
mod ping;
mod state;
mod store;
mod version;

use tauri::Manager;

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let handle = app.handle().clone();
            tauri::async_runtime::block_on(async move {
                paths::ensure_dir(&paths::root()).await?;
                paths::ensure_dir(&paths::shared()).await?;
                paths::ensure_dir(&paths::instances_dir()).await?;
                handle.manage(state::AppState::load().await?);
                Ok::<(), error::Error>(())
            })?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::settings_get,
            commands::settings_set,
            commands::java_detect,
            commands::system_memory_mb,
            commands::versions_manifest,
            commands::versions_cached,
            commands::instances_list,
            commands::instance_create,
            commands::instance_update,
            commands::instance_delete,
            commands::instance_install,
            commands::mods_available,
            commands::mods_list,
            commands::mod_toggle,
            commands::mod_favorite,
            commands::cosmetics_catalog,
            commands::cosmetics_equipped,
            commands::cosmetics_equip,
            commands::hud_get,
            commands::hud_set,
            commands::hud_reset,
            commands::news_feed,
            commands::servers_list,
            commands::servers_save,
            commands::servers_ping,
            commands::auth_list,
            commands::auth_active,
            commands::auth_add_offline,
            commands::auth_select,
            commands::auth_remove,
            commands::auth_begin_device_code,
            commands::auth_poll,
            commands::auth_login_authcode,
            commands::auth_import_official,
            commands::auth_refresh,
            commands::launch,
            commands::launch_quickplay,
            commands::game_kill,
            commands::game_status,
            commands::game_log,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Pinion");
}
