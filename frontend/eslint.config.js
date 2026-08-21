import js from '@eslint/js';
import prettier from 'eslint-config-prettier';
import importPlugin from 'eslint-plugin-import';
import tseslint from 'typescript-eslint';

// Feature 依賴規則 F1–F4(docs/spec/12-frontend.md §12.2),以 import/no-restricted-paths 強制。
// F3(api/generated 勿手改)由 CI 的 `npm run api:check` 驗證(Phase 10 起),
// generated 目錄同時排除於 lint 之外。
const FEATURES = [
  'ioc',
  'threat',
  'stix',
  'sync',
  'auth',
  'subscription',
  'apikey',
  'notification',
  'audit',
];

// F1:features/A 不得 import features/B/**
const f1Zones = FEATURES.map((feature) => ({
  target: `./src/features/${feature}`,
  from: './src/features',
  except: [`./${feature}`],
  message: `F1: features/${feature} 不得 import 其他 feature;共用內容上移至 components/ 或 hooks/`,
}));

// F2:components/、hooks/、utils/ 不得 import features/**
const f2Zones = ['components', 'hooks', 'utils'].map((dir) => ({
  target: `./src/${dir}`,
  from: './src/features',
  message: `F2: ${dir}/ 不得 import features/**`,
}));

// F4:只有 pages/、routes/、app/ 可跨 feature;其餘共用層一律不得 import features/**
const f4Zones = ['layouts', 'stores', 'types', 'api', 'constants', 'styles'].map((dir) => ({
  target: `./src/${dir}`,
  from: './src/features',
  message: `F4: 只有 pages/routes/app 可跨 feature;${dir}/ 不得 import features/**`,
}));

export default tseslint.config(
  {
    ignores: ['dist/', 'coverage/', 'node_modules/', 'src/api/generated/'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { import: importPlugin },
    rules: {
      'import/no-restricted-paths': [
        'error',
        {
          zones: [...f1Zones, ...f2Zones, ...f4Zones],
        },
      ],
    },
  },
  prettier,
);
