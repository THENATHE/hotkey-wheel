# Changes

## 1.0.8+26.3

- Use explicit mouse injector order 900 for the existing wheel ownership handler, ahead of default-order editor mouse injectors. The focused native `key.mouse.4` regression passes without changing editor ownership or public API behavior; the complete fresh release matrix passes 2,188 checks.
- Add native side-mouse press/release regression coverage and repeat the complete minimal, Buildprint, and four-mod native/Polymer acceptance matrix against the exact final JAR.
- Include every freshly captured acceptance screenshot in the release evidence. Keep the original 1.0.7 release and its reviewed source/evidence separate.

## 1.0.7+26.3

Adopted Buildprint 0.1.19's public editor open/close and registered-screen ownership APIs. The separately dated review added all-four integration checks and the full documented API audit while preserving the original production JAR and tag.
