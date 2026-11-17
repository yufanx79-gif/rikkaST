// rikkaST st-compat: scripts/textgen-settings.js
export const textgen_types = {
    OOBA: 'ooba',
    MANCER: 'mancer',
    VLLM: 'vllm',
    APHRODITE: 'aphrodite',
    TABBY: 'tabby',
    KOBOLDCPP: 'koboldcpp',
    TOGETHERAI: 'togetherai',
    LLAMACPP: 'llamacpp',
    OLAPHRODITE: 'olaphrodite',
    INFERMATICAI: 'infermaticai',
    DREAMGEN: 'dreamgen',
    OPENROUTER: 'openrouter',
    FEATHERLESS: 'featherless',
    GENERIC: 'generic',
};
export const textgenerationwebui_settings = { type: 'ooba', temp: 1.0, max_new_tokens: 512 };
export let textgenerationwebui_presets = [];
export let textgenerationwebui_preset_names = {};
export let textgenerationwebui_banned_in_macros = [];
export const setting_names = [
    'temp',
    'temperature_last',
    'rep_pen',
    'rep_pen_range',
    'rep_pen_decay',
    'rep_pen_slope',
    'no_repeat_ngram_size',
    'top_k',
    'top_p',
    'top_a',
    'tfs',
    'epsilon_cutoff',
    'eta_cutoff',
    'typical_p',
    'min_p',
    'penalty_alpha',
    'num_beams',
    'length_penalty',
    'min_length',
    'dynatemp',
    'min_temp',
    'max_temp',
    'dynatemp_exponent',
    'smoothing_factor',
    'smoothing_curve',
    'dry_allowed_length',
    'dry_multiplier',
    'dry_base',
    'dry_sequence_breakers',
    'dry_penalty_last_n',
    'max_tokens_second',
    'encoder_rep_pen',
    'freq_pen',
    'presence_pen',
    'skew',
    'do_sample',
    'early_stopping',
    'seed',
    'add_bos_token',
    'ban_eos_token',
    'skip_special_tokens',
    'include_reasoning',
    'streaming',
    'mirostat_mode',
    'mirostat_tau',
    'mirostat_eta',
    'guidance_scale',
    'negative_prompt',
    'grammar_string',
    'json_schema',
    'banned_tokens',
    'global_banned_tokens',
    'send_banned_tokens',
    'ignore_eos_token',
    'spaces_between_special_tokens',
    'speculative_ngram',
    'sampler_order',
    'sampler_priority',
    'samplers',
    'samplers_priorities',
    'n',
    'logit_bias',
    'custom_model',
    'bypass_status_check',
    'openrouter_allow_fallbacks',
    'xtc_threshold',
    'xtc_probability',
    'nsigma',
    'min_keep',
    'generic_model',
    'extensions',
    'json_schema_allow_empty',
    'adaptive_target',
    'adaptive_decay',
];
export const SERVER_INPUTS = {
    [textgen_types.OOBA]: '#textgenerationwebui_api_url_text',
    [textgen_types.VLLM]: '#vllm_api_url_text',
    [textgen_types.APHRODITE]: '#aphrodite_api_url_text',
    [textgen_types.TABBY]: '#tabby_api_url_text',
    [textgen_types.KOBOLDCPP]: '#koboldcpp_api_url_text',
    [textgen_types.LLAMACPP]: '#llamacpp_api_url_text',
    [textgen_types.OLLAMA]: '#ollama_api_url_text',
    [textgen_types.HUGGINGFACE]: '#huggingface_api_url_text',
    [textgen_types.GENERIC]: '#generic_api_url_text',
};
export const APHRODITE_DEFAULT_ORDER = [
    'dry',
    'penalties',
    'no_repeat_ngram',
    'temperature',
    'top_nsigma',
    'top_p_top_k',
    'top_a',
    'min_p',
    'tfs',
    'eta_cutoff',
    'epsilon_cutoff',
    'typical_p',
    'quadratic',
    'xtc',
];
export let DREAMGEN_SERVER = '';
export let FEATHERLESS_SERVER = '';
export let INFERMATICAI_SERVER = '';
export let MANCER_SERVER = '';
export let OPENROUTER_SERVER = '';
export let TOGETHERAI_SERVER = '';

export function createTextGenGenerationData(prompt) { return { prompt }; }
export function formatTextGenURL(url) { return String(url ?? ''); }
export async function generateTextGenWithStreaming() { return () => ({}); }
export function getLogprobsNumber() { return 0; }
export function getTextGenGenerationData(prompt) { return { prompt }; }
export function getTextGenModel() { return ''; }
export function getTextGenServer() { return ''; }
export function initTextGenSettings() {}
export function isJsonSchemaSupported() { return true; }
export function loadTextGenSettings() {}
export function parseTabbyLogprobs() { return null; }
export function parseTextgenLogprobs() { return null; }
export function replaceMacrosInList(list) { return Array.isArray(list) ? list : []; }
export function showTGSamplerControls() {}
export function validateTextGenUrl(url) { return String(url ?? ''); }
