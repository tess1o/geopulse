import clsx from 'clsx';
import useBaseUrl from '@docusaurus/useBaseUrl';
import ThemedImage from '@theme/ThemedImage';
import styles from './styles.module.css';

// Renders static/img/screenshots/{light,dark}/<name>.webp for the active color mode. Pass dark={false} when only a
// light capture exists; it is then shown in both modes.
export default function Screenshot({name, alt, width, height, dark = true, eager = false, className}) {
    const light = useBaseUrl(`/img/screenshots/light/${name}.webp`);
    const darkSource = useBaseUrl(`/img/screenshots/${dark ? 'dark' : 'light'}/${name}.webp`);

    return (
        <ThemedImage
            className={clsx(styles.screenshot, className)}
            sources={{light, dark: darkSource}}
            alt={alt}
            width={width}
            height={height}
            loading={eager ? 'eager' : 'lazy'}
            decoding="async"
        />
    );
}
