/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import type { PropertyMetadata } from '../api/types'

interface PropertyFieldProps {
  property: PropertyMetadata
  value: unknown
  onChange: (value: unknown) => void
}

/**
 * One form input per {@code PropertyMetadata.type} — the piece of the "typed properties drive an
 * auto-generated form" design that never needs to change when a new piece is added; only when a
 * genuinely new PropertyType is introduced.
 */
export function PropertyField({ property, value, onChange }: PropertyFieldProps) {
  switch (property.type) {
    case 'SHORT_TEXT':
    case 'SECRET_TEXT':
    case 'OAUTH2':
      return (
        <input
          id={property.key}
          type={property.type === 'SHORT_TEXT' ? 'text' : 'password'}
          value={typeof value === 'string' ? value : ''}
          onChange={(e) => onChange(e.target.value)}
        />
      )
    case 'LONG_TEXT':
      return (
        <textarea
          id={property.key}
          rows={3}
          value={typeof value === 'string' ? value : ''}
          onChange={(e) => onChange(e.target.value)}
        />
      )
    case 'NUMBER':
      return (
        <input
          id={property.key}
          type="number"
          value={typeof value === 'number' ? value : ''}
          onChange={(e) => onChange(e.target.value === '' ? undefined : Number(e.target.value))}
        />
      )
    case 'CHECKBOX':
      return (
        <input
          id={property.key}
          type="checkbox"
          checked={Boolean(value)}
          onChange={(e) => onChange(e.target.checked)}
        />
      )
    case 'DROPDOWN':
      return (
        <select id={property.key} value={typeof value === 'string' ? value : ''} onChange={(e) => onChange(e.target.value)}>
          <option value="" disabled>
            Select…
          </option>
          {property.options.map((option) => (
            <option key={String(option.value)} value={String(option.value)}>
              {option.label}
            </option>
          ))}
        </select>
      )
    case 'JSON':
    case 'ARRAY':
      return (
        <textarea
          id={property.key}
          rows={3}
          defaultValue={value === undefined ? '' : JSON.stringify(value, null, 2)}
          onBlur={(e) => {
            if (e.target.value.trim() === '') {
              onChange(undefined)
              return
            }
            try {
              onChange(JSON.parse(e.target.value))
            } catch {
              // Leave the last valid value in place until the JSON is fixed.
            }
          }}
        />
      )
  }
}
