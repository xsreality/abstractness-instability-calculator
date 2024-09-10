{
  inputs = {
    utils.url = "github:numtide/flake-utils";
  };
  outputs = { self, nixpkgs, utils }: utils.lib.eachDefaultSystem (system:
    let
      pkgs = nixpkgs.legacyPackages.${system};
      jdk_headless = pkgs.jdk22_headless;
      maven = pkgs.maven.override { jdk_headless = jdk_headless; };
    in
    {
      devShell = pkgs.mkShell {
        buildInputs = with pkgs; [
          maven
          jdk_headless
        ];
      };
    }
  );
}
