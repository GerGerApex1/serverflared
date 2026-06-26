# ServerFlared

ServerFlared is a mod that automatically creates and manages a Cloudflare Tunnel for your Minecraft server, allowing players to connect without exposing your public IP address or manually configuring port forwarding.

## Requirements

*   A Cloudflare account
*   A domain name connected to Cloudflare
*   (**For client/players only**) [cloudflared](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/) program
    (_this mod does the work of automatic managing cloudflared in your server_)

## Installation

*   Install the mod into `mods` folder
*   Launch Minecraft server
*   Configure `config.yml` and change `tunnelName` and `hostName`
*   Relaunch server to update configuration

## Features
* Automatic Cloudflare Tunnel setup – Creates and manages a Cloudflare Tunnel with minimal configuration by authenticating your Cloudflare account.
* Automatic [cloudflared](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/) download – Downloads the required [cloudflared](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/) binary automatically, reducing manual installation steps. (It automatically works with existing [cloudflared](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/) installation in your machine!)
* Seamless tunnel configuration – Generates and maintains the necessary tunnel configuration, so you only need to specify their tunnel and hostname.
* Integrated tunnel lifecycle – Starts and stops the Cloudflare Tunnel alongside the Minecraft server, ensuring the tunnel is always available while the server is running.
* Secure remote access – Allows players to connect through a Cloudflare-managed hostname instead of exposing the server's IP address.
  Ideal for NAT-restricted networks – Makes hosting possible even when traditional port forwarding is unavailable or impractical. Also works best small servers (e.g. with your friends)


## License

[GPL-3.0](https://github.com/GerGerApex1/serverflared/blob/master/LICENSE)

## References

https://developers.cloudflare.com/cloudflare-one/access-controls/applications/non-http/cloudflared-authentication/arbitrary-tcp/#connect-from-a-client-machine

https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/
