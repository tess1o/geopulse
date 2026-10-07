import React, {useEffect, useRef} from 'react';
import CodeSnippets from '@theme-original/ApiExplorer/CodeSnippets';
import ApiCodeBlock from '@theme/ApiExplorer/ApiCodeBlock';

// The curl sample is generated per operation by scripts/code-samples.mjs (x-codeSamples). It replaces the theme's
// snippets, which are built from the interactive request form and are empty until a reader fills it in.
export default function CodeSnippetsWrapper(props) {
    const sample = (props.codeSamples ?? []).find((codeSample) => codeSample.lang === 'cURL');
    const containerRef = useRef(null);
    useWordWrapByDefault(containerRef);

    if (!sample) {
        return <CodeSnippets {...props} />;
    }
    return (
        <div ref={containerRef} className="openapi-tabs__code-container api-code-sample">
            <ApiCodeBlock language="bash" className="openapi-explorer__code-block">
                {sample.source}
            </ApiCodeBlock>
        </div>
    );
}

// The theme's code block starts unwrapped and only shows its word-wrap toggle once the code overflows. Turn wrapping
// on by pressing that toggle the first time it appears, so readers can still switch it off.
function useWordWrapByDefault(containerRef) {
    useEffect(() => {
        const container = containerRef.current;
        if (!container) {
            return undefined;
        }
        const enableWrap = () => {
            const button = container.querySelector('.openapi-explorer__code-block-word-wrap-btn-icon')?.closest('button');
            if (!button) {
                return false;
            }
            if (!button.classList.contains('openapi-explorer__code-block-word-wrap-btn--enabled')) {
                button.click();
            }
            return true;
        };
        if (enableWrap()) {
            return undefined;
        }
        const observer = new MutationObserver(() => {
            if (enableWrap()) {
                observer.disconnect();
            }
        });
        observer.observe(container, {childList: true, subtree: true});
        return () => observer.disconnect();
    }, [containerRef]);
}
