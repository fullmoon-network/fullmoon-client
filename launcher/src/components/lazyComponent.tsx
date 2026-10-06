import { Suspense, lazy, useState, type ComponentProps, type ComponentType, type ReactNode } from "react";

/* A component loaded on first use. Once its chunk has arrived it renders straight away, with no
   Suspense hop, so a screen change inside a view transition never captures the fallback; before
   that, the fallback holds the component's place. `preload` lets the app fetch the chunk while it
   is idle, so in practice the fallback is only ever seen on a very slow disk. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function lazyComponent<C extends ComponentType<any>>(
  load: () => Promise<{ default: C }>,
  fallback: (props: ComponentProps<C>) => ReactNode = () => null,
) {
  let Loaded: C | null = null;
  let pending: Promise<void> | null = null;
  const preload = () =>
    (pending ??= load().then((m) => {
      Loaded = m.default;
    }));
  const Inner = lazy(() => preload().then(() => ({ default: Loaded as C })));

  function Lazy(props: ComponentProps<C>) {
    /* decided once per mount: swapping an element's type later would remount it and lose its state */
    const [direct] = useState(() => Loaded !== null);
    const Direct = Loaded;
    if (direct && Direct) return <Direct {...props} />;
    return (
      <Suspense fallback={fallback(props)}>
        <Inner {...props} />
      </Suspense>
    );
  }
  return Object.assign(Lazy, { preload });
}
