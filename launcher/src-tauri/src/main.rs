// the launcher is the window; a console behind it is debug-only noise
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod auth;
mod catalog;
mod clips;
mod commands;
mod cosmetics;
mod download;
mod economy;
mod error;
mod fabric;
mod hud;
mod install;
mod java;
mod launch;
mod meta;
mod model;
mod mods;
mod news;
mod obs;
mod offline;
mod paths;
mod ping;
mod shaders;
mod share;
mod state;
mod store;
mod version;

use tauri::Manager;

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_global_shortcut::Builder::new().build())
        .setup(|app| {
            let handle = app.handle().clone();
            tauri::async_runtime::block_on(async move {
                paths::ensure_dir(&paths::root()).await?;
                paths::ensure_dir(&paths::shared()).await?;
                paths::ensure_dir(&paths::instances_dir()).await?;
                paths::ensure_dir(&paths::clips_dir()).await?;
                handle.manage(state::AppState::load().await?);
                Ok::<(), error::Error>(())
            })?;
            // a failed registration is a status the clip screen reports, not a
            // reason to refuse to start — and it runs off the setup thread
            // because registering hops through the main event loop
            let clip_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                clips::install_hotkey(&clip_handle).await;
            });
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::settings_get,
            commands::settings_set,
            commands::java_detect,
            commands::system_memory_mb,
            commands::versions_manifest,
            commands::instances_list,
            commands::instance_create,
            commands::instance_update,
            commands::instance_delete,
            commands::instance_install,
            commands::mods_available,
            commands::mods_list,
            commands::mod_toggle,
            commands::mod_favorite,
            commands::shaders_status,
            commands::shaders_install,
            commands::shaders_set_enabled,
            commands::cosmetics_catalog,
            commands::cosmetics_equipped,
            commands::cosmetics_equip,
            commands::hud_get,
            commands::hud_set,
            commands::hud_reset,
            commands::news_feed,
            commands::economy_wallet,
            commands::economy_transactions,
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
            commands::clips_list,
            commands::clips_status,
            commands::clips_save,
            commands::clips_replay_start,
            commands::clips_replay_stop,
            commands::clips_delete,
            commands::clips_reveal,
            commands::clips_open,
            commands::clips_settings_get,
            commands::clips_settings_set,
            commands::clips_thumbnail,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Pinion");
}
