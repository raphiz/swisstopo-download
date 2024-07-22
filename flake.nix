{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    pre-commit-hooks.url = "github:cachix/pre-commit-hooks.nix";
  };

  outputs = inputs @ {
    self,
    nixpkgs,
    flake-parts,
    pre-commit-hooks,
    ...
  }: let
    version = "0.1.0";
  in
    flake-parts.lib.mkFlake {inherit inputs;} {
      systems = nixpkgs.lib.systems.flakeExposed;
      flake = {
        overlays = {
          dev = final: prev: {
            jdk = prev.jdk21_headless;
            jre_headless = prev.jdk21_headless;
            ktlint = prev.ktlint;
            gradle = prev.callPackage (prev.gradleGen {
              defaultJava = final.jdk;
              version = "8.8";
              nativeVersion = "0.22-milestone-26";
              hash = "sha256-pLQVhgH4Y2ze6rCb12r7ZAAwu1sUSq/iYaXorwJ9xhI=";
            }) {};
          };
        };
      };
      perSystem = {
        config,
        system,
        ...
      }: let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [
            self.overlays.dev
          ];
        };
      in {
        checks = {
          pre-commit-check = pre-commit-hooks.lib.${system}.run {
            src = ./.;
            hooks = {
              alejandra.enable = true;
              ktlint = {
                enable = true;
                name = "ktlint";
                entry = let
                  script = pkgs.writeShellScriptBin "ktlint-wrapper" ''set -euo pipefail; ${pkgs.ktlint}/bin/ktlint --format'';
                in "${script}/bin/ktlint-wrapper";
                files = "\\.(kt|kts)$";
                language = "system";
              };
            };
          };
        };
        devShells.default = let
        in
          pkgs.mkShellNoCC {
            inherit (self.checks.${system}.pre-commit-check) shellHook;
            buildInputs = with pkgs; [jdk gradle];
          };

        formatter = pkgs.alejandra;
      };
    };
}
